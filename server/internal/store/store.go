package store

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
	_ "time/tzdata"

	"golang.org/x/crypto/bcrypt"
	_ "modernc.org/sqlite"
)

var ErrNotFound = errors.New("not found")
var ErrInvalidCredentials = errors.New("invalid credentials")
var ErrDuplicate = errors.New("already exists")
var ErrAccountDisabled = errors.New("account disabled")
var ErrInvalidTimezone = errors.New("invalid timezone")

type Store struct{ db *sql.DB }

type Account struct {
	ID                 string `json:"id"`
	Username           string `json:"username"`
	Role               string `json:"role"`
	Status             string `json:"status"`
	Timezone           string `json:"timezone"`
	MustChangePassword bool   `json:"must_change_password"`
	LastSeenAt         string `json:"last_seen_at,omitempty"`
	LastSyncAt         string `json:"last_sync_at,omitempty"`
}

type Planner struct {
	ID        string `json:"id"`
	AccountID string `json:"account_id"`
}

type Operation struct {
	ID              string          `json:"operation_id"`
	EntityType      string          `json:"entity_type"`
	EntityID        string          `json:"entity_id"`
	Kind            string          `json:"kind"`
	Payload         json.RawMessage `json:"payload"`
	ClientUpdatedAt string          `json:"client_updated_at"`
}

type OperationResult struct {
	OperationID string `json:"operation_id"`
	Status      string `json:"status"`
	Version     int64  `json:"version,omitempty"`
	Error       string `json:"error,omitempty"`
}

type Change struct {
	Cursor      int64           `json:"cursor"`
	OperationID string          `json:"operation_id"`
	EntityType  string          `json:"entity_type"`
	EntityID    string          `json:"entity_id"`
	Kind        string          `json:"kind"`
	Payload     json.RawMessage `json:"payload"`
	Version     int64           `json:"version"`
	UpdatedAt   string          `json:"updated_at"`
}

func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	store := &Store{db: db}
	if _, err := db.Exec(`PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;`); err != nil {
		db.Close()
		return nil, err
	}
	if err := store.migrate(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	return store, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) migrate(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, `
CREATE TABLE IF NOT EXISTS accounts (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('admin', 'member')),
  status TEXT NOT NULL CHECK (status IN ('active', 'disabled')) DEFAULT 'active',
  timezone TEXT NOT NULL DEFAULT 'America/Sao_Paulo',
  must_change_password INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
	last_seen_at TEXT,
	last_sync_at TEXT
);
CREATE TABLE IF NOT EXISTS planners (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL UNIQUE REFERENCES accounts(id),
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT
);
CREATE TABLE IF NOT EXISTS sync_entities (
  account_id TEXT NOT NULL REFERENCES accounts(id),
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  archived INTEGER NOT NULL DEFAULT 0,
  version INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (account_id, entity_type, entity_id)
);
CREATE TABLE IF NOT EXISTS sync_operations (
	operation_id TEXT NOT NULL,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  entity_version INTEGER NOT NULL,
	received_at TEXT NOT NULL,
	PRIMARY KEY (account_id, operation_id)
);
CREATE TABLE IF NOT EXISTS change_log (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  operation_id TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  version INTEGER NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS change_log_account_sequence ON change_log(account_id, sequence);
`)
	return err
}

func (s *Store) BootstrapAdmin(ctx context.Context, username, password string) error {
	var count int
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM accounts`).Scan(&count); err != nil {
		return err
	}
	if count > 0 {
		return nil
	}
	return s.createAccount(ctx, username, password, "admin", "America/Sao_Paulo", false)
}

func (s *Store) createAccount(ctx context.Context, username, password, role, timezone string, mustChange bool) error {
	username = strings.TrimSpace(username)
	if username == "" || password == "" {
		return errors.New("username and password are required")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	id, err := newID()
	if err != nil {
		return err
	}
	now := time.Now().UTC().Format(time.RFC3339Nano)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `INSERT INTO accounts(id, username, password_hash, role, status, timezone, must_change_password, created_at) VALUES (?, ?, ?, ?, 'active', ?, ?, ?)`, id, username, string(hash), role, timezone, boolInt(mustChange), now); err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			return ErrDuplicate
		}
		return err
	}
	plannerID, err := newID()
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO planners(id, account_id, created_at) VALUES (?, ?, ?)`, plannerID, id, now); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) CreateMember(ctx context.Context, username, password, timezone string) error {
	if timezone == "" {
		timezone = "America/Sao_Paulo"
	}
	if _, err := time.LoadLocation(timezone); err != nil {
		return ErrInvalidTimezone
	}
	return s.createAccount(ctx, username, password, "member", timezone, true)
}

func (s *Store) Authenticate(ctx context.Context, username, password string) (Account, string, error) {
	var account Account
	var hash string
	var mustChange int
	var lastSeen sql.NullString
	var lastSync sql.NullString
	err := s.db.QueryRowContext(ctx, `SELECT id, username, password_hash, role, status, timezone, must_change_password, last_seen_at, last_sync_at FROM accounts WHERE username = ?`, username).Scan(&account.ID, &account.Username, &hash, &account.Role, &account.Status, &account.Timezone, &mustChange, &lastSeen, &lastSync)
	if errors.Is(err, sql.ErrNoRows) {
		return Account{}, "", ErrInvalidCredentials
	}
	if err != nil {
		return Account{}, "", err
	}
	if bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) != nil {
		return Account{}, "", ErrInvalidCredentials
	}
	if account.Status != "active" {
		return Account{}, "", ErrAccountDisabled
	}
	account.MustChangePassword = mustChange == 1
	if lastSeen.Valid {
		account.LastSeenAt = lastSeen.String
	}
	if lastSync.Valid {
		account.LastSyncAt = lastSync.String
	}
	raw, err := newID()
	if err != nil {
		return Account{}, "", err
	}
	sessionID, err := newID()
	if err != nil {
		return Account{}, "", err
	}
	tokenHash := hashToken(raw)
	now := time.Now().UTC()
	if _, err := s.db.ExecContext(ctx, `INSERT INTO sessions(id, account_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)`, sessionID, account.ID, tokenHash, now.Format(time.RFC3339Nano), now.Add(30*24*time.Hour).Format(time.RFC3339Nano)); err != nil {
		return Account{}, "", err
	}
	seen := now.Format(time.RFC3339Nano)
	_, _ = s.db.ExecContext(ctx, `UPDATE accounts SET last_seen_at = ? WHERE id = ?`, seen, account.ID)
	account.LastSeenAt = seen
	return account, raw, nil
}

func (s *Store) PlannerForAccount(ctx context.Context, accountID string) (Planner, error) {
	var planner Planner
	err := s.db.QueryRowContext(ctx, `SELECT id, account_id FROM planners WHERE account_id = ?`, accountID).Scan(&planner.ID, &planner.AccountID)
	if errors.Is(err, sql.ErrNoRows) {
		return Planner{}, ErrNotFound
	}
	return planner, err
}

func (s *Store) AccountForToken(ctx context.Context, raw string) (Account, error) {
	var account Account
	var mustChange int
	var lastSeen sql.NullString
	var lastSync sql.NullString
	now := time.Now().UTC().Format(time.RFC3339Nano)
	err := s.db.QueryRowContext(ctx, `SELECT a.id, a.username, a.role, a.status, a.timezone, a.must_change_password, a.last_seen_at, a.last_sync_at FROM sessions s JOIN accounts a ON a.id = s.account_id WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?`, hashToken(raw), now).Scan(&account.ID, &account.Username, &account.Role, &account.Status, &account.Timezone, &mustChange, &lastSeen, &lastSync)
	if errors.Is(err, sql.ErrNoRows) {
		return Account{}, ErrInvalidCredentials
	}
	if err != nil {
		return Account{}, err
	}
	if account.Status != "active" {
		return Account{}, ErrAccountDisabled
	}
	account.MustChangePassword = mustChange == 1
	if lastSeen.Valid {
		account.LastSeenAt = lastSeen.String
	}
	if lastSync.Valid {
		account.LastSyncAt = lastSync.String
	}
	return account, nil
}

func (s *Store) RevokeToken(ctx context.Context, raw string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE sessions SET revoked_at = ? WHERE token_hash = ?`, time.Now().UTC().Format(time.RFC3339Nano), hashToken(raw))
	return err
}

func (s *Store) ChangePassword(ctx context.Context, accountID, password string) error {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, `UPDATE accounts SET password_hash = ?, must_change_password = 0 WHERE id = ?`, string(hash), accountID)
	return err
}

func (s *Store) ListAccounts(ctx context.Context) ([]Account, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id, username, role, status, timezone, must_change_password, last_seen_at, last_sync_at FROM accounts ORDER BY username`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []Account
	for rows.Next() {
		var a Account
		var mustChange int
		var lastSeen sql.NullString
		var lastSync sql.NullString
		if err := rows.Scan(&a.ID, &a.Username, &a.Role, &a.Status, &a.Timezone, &mustChange, &lastSeen, &lastSync); err != nil {
			return nil, err
		}
		a.MustChangePassword = mustChange == 1
		if lastSeen.Valid {
			a.LastSeenAt = lastSeen.String
		}
		if lastSync.Valid {
			a.LastSyncAt = lastSync.String
		}
		result = append(result, a)
	}
	return result, rows.Err()
}

func (s *Store) SetAccountStatus(ctx context.Context, accountID, status string) error {
	if status != "active" && status != "disabled" {
		return errors.New("invalid account status")
	}
	_, err := s.db.ExecContext(ctx, `UPDATE accounts SET status = ? WHERE id = ?`, status, accountID)
	return err
}

func (s *Store) ResetPassword(ctx context.Context, accountID, password string) error {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `UPDATE accounts SET password_hash = ?, must_change_password = 1 WHERE id = ?`, string(hash), accountID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL`, time.Now().UTC().Format(time.RFC3339Nano), accountID); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) ApplyOperations(ctx context.Context, accountID string, operations []Operation) ([]OperationResult, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	now := time.Now().UTC().Format(time.RFC3339Nano)
	results := make([]OperationResult, 0, len(operations))
	for _, op := range operations {
		if op.ID == "" || op.EntityType == "" || op.EntityID == "" || (op.Kind != "upsert" && op.Kind != "archive") || !json.Valid(op.Payload) {
			results = append(results, OperationResult{OperationID: op.ID, Status: "rejected", Error: "invalid operation"})
			continue
		}
		var accountTimezone string
		switch op.EntityType {
		case "task", "routine", "routine_occurrence":
		case "account_settings":
			var settings struct {
				Timezone string `json:"timezone"`
			}
			if op.Kind != "upsert" || op.EntityID != accountID || json.Unmarshal(op.Payload, &settings) != nil {
				results = append(results, OperationResult{OperationID: op.ID, Status: "rejected", Error: "invalid account settings"})
				continue
			}
			if _, err := time.LoadLocation(settings.Timezone); err != nil {
				results = append(results, OperationResult{OperationID: op.ID, Status: "rejected", Error: "invalid timezone"})
				continue
			}
			accountTimezone = settings.Timezone
		default:
			results = append(results, OperationResult{OperationID: op.ID, Status: "rejected", Error: "unsupported entity type"})
			continue
		}
		var duplicateVersion int64
		err := tx.QueryRowContext(ctx, `SELECT entity_version FROM sync_operations WHERE account_id = ? AND operation_id = ?`, accountID, op.ID).Scan(&duplicateVersion)
		if err == nil {
			results = append(results, OperationResult{OperationID: op.ID, Status: "duplicate", Version: duplicateVersion})
			continue
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		payload := string(op.Payload)
		var version int64
		if err := tx.QueryRowContext(ctx, `SELECT COALESCE(version, 0) + 1 FROM sync_entities WHERE account_id = ? AND entity_type = ? AND entity_id = ?`, accountID, op.EntityType, op.EntityID).Scan(&version); err != nil {
			if !errors.Is(err, sql.ErrNoRows) {
				return nil, err
			}
			version = 1
		}
		archived := 0
		if op.Kind == "archive" {
			archived = 1
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO sync_entities(account_id, entity_type, entity_id, payload_json, archived, version, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(account_id, entity_type, entity_id) DO UPDATE SET payload_json = excluded.payload_json, archived = excluded.archived, version = excluded.version, updated_at = excluded.updated_at`, accountID, op.EntityType, op.EntityID, payload, archived, version, now); err != nil {
			return nil, err
		}
		if accountTimezone != "" {
			if _, err := tx.ExecContext(ctx, `UPDATE accounts SET timezone = ? WHERE id = ?`, accountTimezone, accountID); err != nil {
				return nil, err
			}
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO change_log(account_id, operation_id, entity_type, entity_id, kind, payload_json, version, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`, accountID, op.ID, op.EntityType, op.EntityID, op.Kind, payload, version, now); err != nil {
			return nil, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO sync_operations(operation_id, account_id, entity_version, received_at) VALUES (?, ?, ?, ?)`, op.ID, accountID, version, now); err != nil {
			return nil, err
		}
		results = append(results, OperationResult{OperationID: op.ID, Status: "accepted", Version: version})
	}
	if _, err := tx.ExecContext(ctx, `UPDATE accounts SET last_sync_at = ? WHERE id = ?`, now, accountID); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return results, nil
}

func (s *Store) PullChanges(ctx context.Context, accountID string, cursor int64, limit int) ([]Change, int64, bool, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `SELECT sequence, operation_id, entity_type, entity_id, kind, payload_json, version, updated_at FROM change_log WHERE account_id = ? AND sequence > ? ORDER BY sequence LIMIT ?`, accountID, cursor, limit+1)
	if err != nil {
		return nil, cursor, false, err
	}
	defer rows.Close()
	changes := make([]Change, 0)
	next := cursor
	for rows.Next() {
		var c Change
		var payload string
		if err := rows.Scan(&c.Cursor, &c.OperationID, &c.EntityType, &c.EntityID, &c.Kind, &payload, &c.Version, &c.UpdatedAt); err != nil {
			return nil, cursor, false, err
		}
		c.Payload = json.RawMessage(payload)
		changes = append(changes, c)
	}
	if err := rows.Err(); err != nil {
		return nil, cursor, false, err
	}
	hasMore := len(changes) > limit
	if hasMore {
		changes = changes[:limit]
	}
	if len(changes) > 0 {
		next = changes[len(changes)-1].Cursor
	}
	if err := rows.Close(); err != nil {
		return nil, cursor, false, err
	}
	if _, err := s.db.ExecContext(ctx, `UPDATE accounts SET last_sync_at = ? WHERE id = ?`, time.Now().UTC().Format(time.RFC3339Nano), accountID); err != nil {
		return nil, cursor, false, err
	}
	return changes, next, hasMore, nil
}

func newID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func hashToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

func boolInt(v bool) int {
	if v {
		return 1
	}
	return 0
}

func (a Account) String() string { return fmt.Sprintf("%s (%s)", a.Username, a.Role) }

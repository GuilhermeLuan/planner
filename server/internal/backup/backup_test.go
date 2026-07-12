package backup_test

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"planner/server/internal/app"
	"planner/server/internal/backup"
)

func TestBackupRestoresAccountsPlannersAndSyncState(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	sourcePath := filepath.Join(t.TempDir(), "planner.db")
	source, err := app.Open(app.Config{
		DatabasePath:  sourcePath,
		AdminUsername: "owner",
		AdminPassword: "owner-password",
	}, logger)
	if err != nil {
		t.Fatalf("open source installation: %v", err)
	}
	t.Cleanup(func() { _ = source.Close() })
	token := login(t, source.Handler(), "owner", "owner-password")
	pushTask(t, source.Handler(), token)

	result, err := backup.Create(context.Background(), backup.Config{
		DatabasePath:   sourcePath,
		DestinationDir: filepath.Join(t.TempDir(), "backups"),
		RetentionDays:  14,
	})
	if err != nil {
		t.Fatalf("create backup: %v", err)
	}

	restored, err := app.Open(app.Config{
		DatabasePath:  result.Path,
		AdminUsername: "replacement",
		AdminPassword: "replacement-password",
	}, logger)
	if err != nil {
		t.Fatalf("open restored installation: %v", err)
	}
	t.Cleanup(func() { _ = restored.Close() })
	restoredToken := login(t, restored.Handler(), "owner", "owner-password")

	request := httptest.NewRequest(http.MethodGet, "/api/v1/sync/pull?cursor=0&limit=10", nil)
	request.Header.Set("Authorization", "Bearer "+restoredToken)
	response := httptest.NewRecorder()
	restored.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("pull restored state: %d %s", response.Code, response.Body.String())
	}
	var pull struct {
		Changes []struct {
			EntityID string          `json:"entity_id"`
			Payload  json.RawMessage `json:"payload"`
		} `json:"changes"`
	}
	if err := json.NewDecoder(response.Body).Decode(&pull); err != nil {
		t.Fatalf("decode restored pull: %v", err)
	}
	if len(pull.Changes) != 1 || pull.Changes[0].EntityID != "backup-task" || !bytes.Contains(pull.Changes[0].Payload, []byte("Persistida no backup")) {
		t.Fatalf("unexpected restored state: %+v", pull.Changes)
	}
}

func login(t *testing.T, handler http.Handler, username, password string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"username": username, "password": password})
	request := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("login: %d %s", response.Code, response.Body.String())
	}
	var session struct {
		Token string `json:"token"`
	}
	if err := json.NewDecoder(response.Body).Decode(&session); err != nil {
		t.Fatalf("decode login: %v", err)
	}
	return session.Token
}

func pushTask(t *testing.T, handler http.Handler, token string) {
	t.Helper()
	body := bytes.NewBufferString(`{"operations":[{"operation_id":"backup-operation","entity_type":"task","entity_id":"backup-task","kind":"upsert","payload":{"title":"Persistida no backup","day":"2026-07-11"}}]}`)
	request := httptest.NewRequest(http.MethodPost, "/api/v1/sync/push", body)
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("push task: %d %s", response.Code, response.Body.String())
	}
}

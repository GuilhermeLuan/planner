package app_test

import (
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"planner/server/internal/app"
)

func TestInstallationBootstrapsAdminAndPlannerOnlyOnce(t *testing.T) {
	databasePath := filepath.Join(t.TempDir(), "planner.db")
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))

	first, err := app.Open(app.Config{
		DatabasePath:  databasePath,
		AdminUsername: "owner",
		AdminPassword: "first-password",
	}, logger)
	if err != nil {
		t.Fatalf("open first installation: %v", err)
	}
	firstSession := login(t, first.Handler(), "owner", "first-password")
	if firstSession.Account.Username != "owner" || firstSession.Account.Role != "admin" {
		t.Fatalf("unexpected bootstrapped account: %+v", firstSession.Account)
	}
	if firstSession.Planner.ID == "" || firstSession.Planner.AccountID != firstSession.Account.ID {
		t.Fatalf("unexpected bootstrapped planner: %+v", firstSession.Planner)
	}
	if err := first.Close(); err != nil {
		t.Fatalf("close first installation: %v", err)
	}

	second, err := app.Open(app.Config{
		DatabasePath:  databasePath,
		AdminUsername: "replacement",
		AdminPassword: "replacement-password",
	}, logger)
	if err != nil {
		t.Fatalf("reopen installation: %v", err)
	}
	t.Cleanup(func() { _ = second.Close() })

	reopenedSession := login(t, second.Handler(), "owner", "first-password")
	if reopenedSession.Account.ID != firstSession.Account.ID {
		t.Fatalf("expected account %q after restart, got %q", firstSession.Account.ID, reopenedSession.Account.ID)
	}
	if reopenedSession.Planner.ID != firstSession.Planner.ID {
		t.Fatalf("expected planner %q after restart, got %q", firstSession.Planner.ID, reopenedSession.Planner.ID)
	}

	request := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString(`{"username":"replacement","password":"replacement-password"}`))
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	second.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("expected replacement credentials to be rejected, got %d", response.Code)
	}
}

type loginResponse struct {
	Token   string `json:"token"`
	Account struct {
		ID       string `json:"id"`
		Username string `json:"username"`
		Role     string `json:"role"`
	} `json:"account"`
	Planner struct {
		ID        string `json:"id"`
		AccountID string `json:"account_id"`
	} `json:"planner"`
}

func login(t *testing.T, handler http.Handler, username, password string) loginResponse {
	t.Helper()
	body, err := json.Marshal(map[string]string{"username": username, "password": password})
	if err != nil {
		t.Fatalf("marshal login request: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected login status 200, got %d: %s", response.Code, response.Body.String())
	}
	var session loginResponse
	if err := json.NewDecoder(response.Body).Decode(&session); err != nil {
		t.Fatalf("decode login response: %v", err)
	}
	if session.Token == "" {
		t.Fatal("expected non-empty session token")
	}
	return session
}

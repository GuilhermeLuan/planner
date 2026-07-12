package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"planner/server/internal/httpapi"
	"planner/server/internal/store"
)

func TestHealthCheckReportsServiceReady(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })

	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}

	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()
	server.Router().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected status %d, got %d", http.StatusOK, response.Code)
	}
	if got := response.Header().Get("Content-Type"); got != "application/json; charset=utf-8" {
		t.Fatalf("expected JSON content type, got %q", got)
	}
	if got := response.Body.String(); got != "{\"status\":\"ok\"}\n" {
		t.Fatalf("unexpected response body: %s", got)
	}
}

func TestAPIRejectsTrailingJSONInsteadOfPartiallyProcessingIt(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString(`{"username":"owner","password":"owner-password"} {}`))
	response := httptest.NewRecorder()
	server.Router().ServeHTTP(response, request)
	if response.Code != http.StatusBadRequest {
		t.Fatalf("expected trailing JSON to be rejected, got %d: %s", response.Code, response.Body.String())
	}
}

func TestOnlyAdministratorSessionCanAccessAdminPanel(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), "member", "member-password", "America/Sao_Paulo"); err != nil {
		t.Fatalf("create member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}

	visitorRequest := httptest.NewRequest(http.MethodGet, "/admin/accounts", nil)
	visitorResponse := httptest.NewRecorder()
	server.Router().ServeHTTP(visitorResponse, visitorRequest)
	if visitorResponse.Code != http.StatusSeeOther || visitorResponse.Header().Get("Location") != "/admin/login" {
		t.Fatalf("expected visitor redirect to login, got %d %q", visitorResponse.Code, visitorResponse.Header().Get("Location"))
	}

	memberLogin := postForm(server.Router(), "/admin/login", url.Values{
		"username": {"member"},
		"password": {"member-password"},
	})
	if memberLogin.Code != http.StatusForbidden {
		t.Fatalf("expected member login to be forbidden, got %d", memberLogin.Code)
	}

	adminLogin := postForm(server.Router(), "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})
	if adminLogin.Code != http.StatusSeeOther || adminLogin.Header().Get("Location") != "/admin/accounts" {
		t.Fatalf("expected admin redirect to accounts, got %d %q", adminLogin.Code, adminLogin.Header().Get("Location"))
	}
	cookies := adminLogin.Result().Cookies()
	if len(cookies) != 1 || !cookies[0].HttpOnly {
		t.Fatalf("expected one HTTP-only admin session cookie, got %+v", cookies)
	}

	accountsRequest := httptest.NewRequest(http.MethodGet, "/admin/accounts", nil)
	accountsRequest.AddCookie(cookies[0])
	accountsResponse := httptest.NewRecorder()
	server.Router().ServeHTTP(accountsResponse, accountsRequest)
	if accountsResponse.Code != http.StatusOK {
		t.Fatalf("expected admin access, got %d: %s", accountsResponse.Code, accountsResponse.Body.String())
	}
}

func TestAdminSessionCookieIsSecureWhenRequestUsesHTTPS(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/admin/login", strings.NewReader(url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	}.Encode()))
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	request.Header.Set("X-Forwarded-Proto", "https")
	response := httptest.NewRecorder()
	server.Router().ServeHTTP(response, request)
	cookies := response.Result().Cookies()
	if len(cookies) != 1 || !cookies[0].Secure {
		t.Fatalf("expected secure admin cookie over HTTPS, got %+v", cookies)
	}
}

func TestAdministratorCreatesAccountVisibleInPanelAndLoginAPI(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})
	adminCookie := adminLogin.Result().Cookies()[0]

	createRequest := httptest.NewRequest(http.MethodPost, "/admin/accounts", strings.NewReader(url.Values{
		"username": {"ana"},
		"password": {"temporary-password"},
		"timezone": {"Europe/Lisbon"},
	}.Encode()))
	createRequest.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	createRequest.AddCookie(adminCookie)
	createResponse := httptest.NewRecorder()
	handler.ServeHTTP(createResponse, createRequest)
	if createResponse.Code != http.StatusSeeOther {
		t.Fatalf("expected account creation redirect, got %d: %s", createResponse.Code, createResponse.Body.String())
	}

	memberSession := login(t, handler, "ana", "temporary-password")
	if memberSession.Account.Username != "ana" || memberSession.Account.Timezone != "Europe/Lisbon" || !memberSession.Account.MustChangePassword {
		t.Fatalf("unexpected created account: %+v", memberSession.Account)
	}

	listRequest := httptest.NewRequest(http.MethodGet, "/admin/accounts", nil)
	listRequest.AddCookie(adminCookie)
	listResponse := httptest.NewRecorder()
	handler.ServeHTTP(listResponse, listRequest)
	if listResponse.Code != http.StatusOK {
		t.Fatalf("expected accounts page, got %d", listResponse.Code)
	}
	body := listResponse.Body.String()
	if !strings.Contains(body, "ana") || !strings.Contains(body, "Europe/Lisbon") {
		t.Fatalf("expected created account metadata in panel: %s", body)
	}
	if strings.Contains(body, memberSession.Planner.ID) || strings.Contains(body, "planner_id") {
		t.Fatalf("admin panel leaked Planner data: %s", body)
	}
}

func TestAdministratorGetsLegibleErrorForDuplicateUsername(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), "ana", "temporary-password", "Europe/Lisbon"); err != nil {
		t.Fatalf("create existing member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})

	request := httptest.NewRequest(http.MethodPost, "/admin/accounts", strings.NewReader(url.Values{
		"username": {"ana"},
		"password": {"another-password"},
		"timezone": {"America/Sao_Paulo"},
	}.Encode()))
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	request.AddCookie(adminLogin.Result().Cookies()[0])
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)

	if response.Code != http.StatusConflict {
		t.Fatalf("expected duplicate conflict, got %d", response.Code)
	}
	if !strings.Contains(response.Body.String(), "já existe") {
		t.Fatalf("expected legible duplicate message, got %q", response.Body.String())
	}
}

func TestDisabledAccountIsRejectedOnLoginAndNextAuthenticatedContact(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), "ana", "temporary-password", "Europe/Lisbon"); err != nil {
		t.Fatalf("create member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	memberSession := login(t, handler, "ana", "temporary-password")
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})

	disableRequest := httptest.NewRequest(http.MethodPost, "/admin/accounts/"+memberSession.Account.ID+"/disable", nil)
	disableRequest.AddCookie(adminLogin.Result().Cookies()[0])
	disableResponse := httptest.NewRecorder()
	handler.ServeHTTP(disableResponse, disableRequest)
	if disableResponse.Code != http.StatusSeeOther {
		t.Fatalf("expected disable redirect, got %d: %s", disableResponse.Code, disableResponse.Body.String())
	}

	loginBody := bytes.NewBufferString(`{"username":"ana","password":"temporary-password"}`)
	loginRequest := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", loginBody)
	loginRequest.Header.Set("Content-Type", "application/json")
	loginResponse := httptest.NewRecorder()
	handler.ServeHTTP(loginResponse, loginRequest)
	assertAccountDisabled(t, loginResponse)

	syncRequest := httptest.NewRequest(http.MethodGet, "/api/v1/sync/pull?cursor=0", nil)
	syncRequest.Header.Set("Authorization", "Bearer "+memberSession.Token)
	syncResponse := httptest.NewRecorder()
	handler.ServeHTTP(syncResponse, syncRequest)
	assertAccountDisabled(t, syncResponse)

	activateRequest := httptest.NewRequest(http.MethodPost, "/admin/accounts/"+memberSession.Account.ID+"/activate", nil)
	activateRequest.AddCookie(adminLogin.Result().Cookies()[0])
	activateResponse := httptest.NewRecorder()
	handler.ServeHTTP(activateResponse, activateRequest)
	if activateResponse.Code != http.StatusSeeOther {
		t.Fatalf("expected activate redirect, got %d", activateResponse.Code)
	}
	recoveredSession := login(t, handler, "ana", "temporary-password")
	if recoveredSession.Planner.ID != memberSession.Planner.ID {
		t.Fatalf("expected preserved Planner %q, got %q", memberSession.Planner.ID, recoveredSession.Planner.ID)
	}
}

func TestAdminPasswordResetRevokesSessionsAndRequiresAnotherChange(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), "ana", "old-temporary-password", "Europe/Lisbon"); err != nil {
		t.Fatalf("create member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	oldSession := login(t, handler, "ana", "old-temporary-password")
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})

	resetRequest := httptest.NewRequest(http.MethodPost, "/admin/accounts/"+oldSession.Account.ID+"/reset-password", strings.NewReader(url.Values{
		"password": {"new-temporary-password"},
	}.Encode()))
	resetRequest.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resetRequest.AddCookie(adminLogin.Result().Cookies()[0])
	resetResponse := httptest.NewRecorder()
	handler.ServeHTTP(resetResponse, resetRequest)
	if resetResponse.Code != http.StatusSeeOther {
		t.Fatalf("expected password reset redirect, got %d: %s", resetResponse.Code, resetResponse.Body.String())
	}

	oldSessionRequest := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
	oldSessionRequest.Header.Set("Authorization", "Bearer "+oldSession.Token)
	oldSessionResponse := httptest.NewRecorder()
	handler.ServeHTTP(oldSessionResponse, oldSessionRequest)
	if oldSessionResponse.Code != http.StatusUnauthorized {
		t.Fatalf("expected old session to be revoked, got %d", oldSessionResponse.Code)
	}

	oldPasswordRequest := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString(`{"username":"ana","password":"old-temporary-password"}`))
	oldPasswordResponse := httptest.NewRecorder()
	handler.ServeHTTP(oldPasswordResponse, oldPasswordRequest)
	if oldPasswordResponse.Code != http.StatusUnauthorized {
		t.Fatalf("expected old password to be rejected, got %d", oldPasswordResponse.Code)
	}

	newSession := login(t, handler, "ana", "new-temporary-password")
	if !newSession.Account.MustChangePassword {
		t.Fatal("expected reset password to require another change")
	}
	if newSession.Planner.ID != oldSession.Planner.ID {
		t.Fatalf("expected password reset to preserve Planner %q, got %q", oldSession.Planner.ID, newSession.Planner.ID)
	}
}

func TestTemporaryPasswordMustBeChangedBeforePlannerAccess(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), "ana", "temporary-password", "Europe/Lisbon"); err != nil {
		t.Fatalf("create member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	session := login(t, handler, "ana", "temporary-password")

	blockedRequest := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
	blockedRequest.Header.Set("Authorization", "Bearer "+session.Token)
	blockedResponse := httptest.NewRecorder()
	handler.ServeHTTP(blockedResponse, blockedRequest)
	if blockedResponse.Code != http.StatusForbidden || !strings.Contains(blockedResponse.Body.String(), `"code":"password_change_required"`) {
		t.Fatalf("expected password-change requirement, got %d: %s", blockedResponse.Code, blockedResponse.Body.String())
	}

	changeRequest := httptest.NewRequest(http.MethodPost, "/api/v1/auth/change-password", bytes.NewBufferString(`{"password":"permanent-password"}`))
	changeRequest.Header.Set("Authorization", "Bearer "+session.Token)
	changeResponse := httptest.NewRecorder()
	handler.ServeHTTP(changeResponse, changeRequest)
	if changeResponse.Code != http.StatusOK {
		t.Fatalf("expected password change, got %d: %s", changeResponse.Code, changeResponse.Body.String())
	}

	allowedRequest := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
	allowedRequest.Header.Set("Authorization", "Bearer "+session.Token)
	allowedResponse := httptest.NewRecorder()
	handler.ServeHTTP(allowedResponse, allowedRequest)
	if allowedResponse.Code != http.StatusOK {
		t.Fatalf("expected Planner access after password change, got %d: %s", allowedResponse.Code, allowedResponse.Body.String())
	}
}

func TestAdminLogoutInvalidatesServerSessionAndCookie(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})
	sessionCookie := adminLogin.Result().Cookies()[0]

	logoutRequest := httptest.NewRequest(http.MethodPost, "/admin/logout", nil)
	logoutRequest.AddCookie(sessionCookie)
	logoutResponse := httptest.NewRecorder()
	handler.ServeHTTP(logoutResponse, logoutRequest)
	if logoutResponse.Code != http.StatusSeeOther || logoutResponse.Header().Get("Location") != "/admin/login" {
		t.Fatalf("expected logout redirect, got %d %q", logoutResponse.Code, logoutResponse.Header().Get("Location"))
	}
	clearedCookies := logoutResponse.Result().Cookies()
	if len(clearedCookies) != 1 || clearedCookies[0].MaxAge >= 0 {
		t.Fatalf("expected expired admin cookie, got %+v", clearedCookies)
	}

	reuseRequest := httptest.NewRequest(http.MethodGet, "/admin/accounts", nil)
	reuseRequest.AddCookie(sessionCookie)
	reuseResponse := httptest.NewRecorder()
	handler.ServeHTTP(reuseResponse, reuseRequest)
	if reuseResponse.Code != http.StatusSeeOther || reuseResponse.Header().Get("Location") != "/admin/login" {
		t.Fatalf("expected reused session to be rejected, got %d", reuseResponse.Code)
	}
}

func TestSyncPushReportsAcceptedAndIdempotentRetryExplicitly(t *testing.T) {
	handler, session := readyMemberServer(t, "ana")
	operation := map[string]any{
		"operation_id":      "op-create-task-1",
		"entity_type":       "task",
		"entity_id":         "task-1",
		"kind":              "upsert",
		"payload":           map[string]any{"title": "Comprar café", "day": "2026-07-11"},
		"client_updated_at": "2026-07-11T10:00:00Z",
	}

	first := pushOperations(t, handler, session.Token, []map[string]any{operation})
	if len(first.Results) != 1 || first.Results[0].OperationID != "op-create-task-1" || first.Results[0].Status != "accepted" || first.Results[0].Version != 1 {
		t.Fatalf("unexpected first push result: %+v", first.Results)
	}

	retry := pushOperations(t, handler, session.Token, []map[string]any{operation})
	if len(retry.Results) != 1 || retry.Results[0].Status != "duplicate" || retry.Results[0].Version != 1 {
		t.Fatalf("unexpected retry result: %+v", retry.Results)
	}

	pullRequest := httptest.NewRequest(http.MethodGet, "/api/v1/sync/pull?cursor=0", nil)
	pullRequest.Header.Set("Authorization", "Bearer "+session.Token)
	pullResponse := httptest.NewRecorder()
	handler.ServeHTTP(pullResponse, pullRequest)
	if pullResponse.Code != http.StatusOK {
		t.Fatalf("expected pull status 200, got %d: %s", pullResponse.Code, pullResponse.Body.String())
	}
	var pull struct {
		Changes []json.RawMessage `json:"changes"`
	}
	if err := json.NewDecoder(pullResponse.Body).Decode(&pull); err != nil {
		t.Fatalf("decode pull response: %v", err)
	}
	if len(pull.Changes) != 1 {
		t.Fatalf("expected one canonical change after retry, got %d", len(pull.Changes))
	}
}

func TestSyncPullPaginatesAndNeverCrossesAccountBoundary(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	for _, username := range []string{"ana", "bia"} {
		if err := database.CreateMember(context.Background(), username, "temporary-password", "America/Sao_Paulo"); err != nil {
			t.Fatalf("create member %s: %v", username, err)
		}
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	ana := loginAndChangePassword(t, handler, "ana", "ana-permanent-password")
	bia := loginAndChangePassword(t, handler, "bia", "bia-permanent-password")

	pushOperations(t, handler, ana.Token, []map[string]any{
		{"operation_id": "shared-operation", "entity_type": "task", "entity_id": "ana-task-1", "kind": "upsert", "payload": map[string]any{"title": "Primeira da Ana"}},
		{"operation_id": "ana-operation-2", "entity_type": "task", "entity_id": "ana-task-2", "kind": "upsert", "payload": map[string]any{"title": "Segunda da Ana"}},
	})
	biaPush := pushOperations(t, handler, bia.Token, []map[string]any{
		{"operation_id": "shared-operation", "entity_type": "task", "entity_id": "bia-task-1", "kind": "upsert", "payload": map[string]any{"title": "Somente da Bia"}},
	})
	if biaPush.Results[0].Status != "accepted" {
		t.Fatalf("expected operation ID to be scoped by Conta, got %+v", biaPush.Results[0])
	}

	firstPage := pullChanges(t, handler, ana.Token, 0, 1)
	if len(firstPage.Changes) != 1 || !firstPage.HasMore || firstPage.NextCursor == 0 {
		t.Fatalf("unexpected first page: %+v", firstPage)
	}
	secondPage := pullChanges(t, handler, ana.Token, firstPage.NextCursor, 1)
	if len(secondPage.Changes) != 1 || secondPage.HasMore {
		t.Fatalf("unexpected second page: %+v", secondPage)
	}
	if secondPage.Changes[0].EntityID == firstPage.Changes[0].EntityID {
		t.Fatalf("expected distinct changes across pages: %+v %+v", firstPage, secondPage)
	}

	biaPage := pullChanges(t, handler, bia.Token, 0, 10)
	if len(biaPage.Changes) != 1 || biaPage.Changes[0].EntityID != "bia-task-1" || strings.Contains(string(biaPage.Changes[0].Payload), "Ana") {
		t.Fatalf("Conta boundary was crossed: %+v", biaPage)
	}
}

func TestAdminPanelShowsLastSuccessfulAccountSync(t *testing.T) {
	handler, session := readyMemberServer(t, "ana")
	pushOperations(t, handler, session.Token, []map[string]any{
		{"operation_id": "sync-metadata-operation", "entity_type": "task", "entity_id": "private-task", "kind": "upsert", "payload": map[string]any{"title": "Conteúdo privado"}},
	})
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})
	request := httptest.NewRequest(http.MethodGet, "/admin/accounts", nil)
	request.AddCookie(adminLogin.Result().Cookies()[0])
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected accounts page, got %d", response.Code)
	}
	body := response.Body.String()
	if !strings.Contains(body, "Última sincronização") || !regexp.MustCompile(`data-field="last-sync">[^<]+</td>`).MatchString(body) {
		t.Fatalf("expected last sync metadata in panel: %s", body)
	}
	if strings.Contains(body, "Conteúdo privado") || strings.Contains(body, "private-task") {
		t.Fatalf("admin panel leaked synchronized Planner content: %s", body)
	}
}

func TestAccountTimezoneChangeSyncsIntoAccountIdentity(t *testing.T) {
	handler, session := readyMemberServer(t, "ana")
	result := pushOperations(t, handler, session.Token, []map[string]any{
		{
			"operation_id": "change-account-timezone",
			"entity_type":  "account_settings",
			"entity_id":    session.Account.ID,
			"kind":         "upsert",
			"payload":      map[string]any{"timezone": "Europe/Lisbon"},
		},
	})
	if len(result.Results) != 1 || result.Results[0].Status != "accepted" {
		t.Fatalf("expected timezone operation to be accepted: %+v", result.Results)
	}

	request := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
	request.Header.Set("Authorization", "Bearer "+session.Token)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected account identity, got %d: %s", response.Code, response.Body.String())
	}
	var account store.Account
	if err := json.NewDecoder(response.Body).Decode(&account); err != nil {
		t.Fatalf("decode account identity: %v", err)
	}
	if account.Timezone != "Europe/Lisbon" {
		t.Fatalf("expected synced timezone, got %q", account.Timezone)
	}
}

func TestSyncRejectsInvalidOperationWithoutDroppingItsResult(t *testing.T) {
	handler, session := readyMemberServer(t, "ana")
	result := pushOperations(t, handler, session.Token, []map[string]any{
		{"operation_id": "invalid-operation", "entity_type": "unknown", "entity_id": "x", "kind": "upsert", "payload": map[string]any{}},
		{"operation_id": "valid-operation", "entity_type": "task", "entity_id": "task-1", "kind": "upsert", "payload": map[string]any{"title": "Visível"}},
	})
	if len(result.Results) != 2 {
		t.Fatalf("expected one result per operation, got %+v", result.Results)
	}
	if result.Results[0].Status != "rejected" || result.Results[0].Error == "" {
		t.Fatalf("expected explicit rejection, got %+v", result.Results[0])
	}
	if result.Results[1].Status != "accepted" {
		t.Fatalf("expected valid operation in same batch to succeed, got %+v", result.Results[1])
	}
}

func TestLastServerAcceptedTaskVersionWinsConcurrentChanges(t *testing.T) {
	handler, firstClient := readyMemberServer(t, "ana")
	secondClient := login(t, handler, "ana", "permanent-password")

	firstResult := pushOperations(t, handler, firstClient.Token, []map[string]any{
		{"operation_id": "client-a-update", "entity_type": "task", "entity_id": "shared-task", "kind": "upsert", "payload": map[string]any{"title": "Versão A"}},
	})
	secondResult := pushOperations(t, handler, secondClient.Token, []map[string]any{
		{"operation_id": "client-b-update", "entity_type": "task", "entity_id": "shared-task", "kind": "upsert", "payload": map[string]any{"title": "Versão B"}},
	})
	if firstResult.Results[0].Version != 1 || secondResult.Results[0].Version != 2 {
		t.Fatalf("expected monotonic server versions, got %+v then %+v", firstResult.Results, secondResult.Results)
	}
	changes := pullChanges(t, handler, firstClient.Token, 0, 10)
	if len(changes.Changes) != 2 || changes.Changes[1].Version != 2 || !strings.Contains(string(changes.Changes[1].Payload), "Versão B") {
		t.Fatalf("expected last accepted version to be final observable change: %+v", changes)
	}
}

func TestAdminAccountCreationRejectsInvalidTimezoneWithoutPartialAccount(t *testing.T) {
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	adminLogin := postForm(handler, "/admin/login", url.Values{
		"username": {"owner"},
		"password": {"owner-password"},
	})
	request := httptest.NewRequest(http.MethodPost, "/admin/accounts", strings.NewReader(url.Values{
		"username": {"ana"},
		"password": {"temporary-password"},
		"timezone": {"Mars/Olympus_Mons"},
	}.Encode()))
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	request.AddCookie(adminLogin.Result().Cookies()[0])
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusBadRequest || !strings.Contains(response.Body.String(), "fuso") {
		t.Fatalf("expected legible timezone error, got %d: %s", response.Code, response.Body.String())
	}

	loginRequest := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString(`{"username":"ana","password":"temporary-password"}`))
	loginResponse := httptest.NewRecorder()
	handler.ServeHTTP(loginResponse, loginRequest)
	if loginResponse.Code != http.StatusUnauthorized {
		t.Fatalf("expected invalid account not to exist, got login status %d", loginResponse.Code)
	}
}

type pullResponse struct {
	NextCursor int64 `json:"next_cursor"`
	HasMore    bool  `json:"has_more"`
	Changes    []struct {
		Cursor     int64           `json:"cursor"`
		EntityType string          `json:"entity_type"`
		EntityID   string          `json:"entity_id"`
		Payload    json.RawMessage `json:"payload"`
		Version    int64           `json:"version"`
	} `json:"changes"`
}

func pullChanges(t *testing.T, handler http.Handler, token string, cursor int64, limit int) pullResponse {
	t.Helper()
	target := fmt.Sprintf("/api/v1/sync/pull?cursor=%d&limit=%d", cursor, limit)
	request := httptest.NewRequest(http.MethodGet, target, nil)
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected pull status 200, got %d: %s", response.Code, response.Body.String())
	}
	var result pullResponse
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		t.Fatalf("decode pull response: %v", err)
	}
	return result
}

func loginAndChangePassword(t *testing.T, handler http.Handler, username, password string) sessionResponse {
	t.Helper()
	session := login(t, handler, username, "temporary-password")
	body, err := json.Marshal(map[string]string{"password": password})
	if err != nil {
		t.Fatalf("marshal password change: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/api/v1/auth/change-password", bytes.NewReader(body))
	request.Header.Set("Authorization", "Bearer "+session.Token)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("change password for %s: %d %s", username, response.Code, response.Body.String())
	}
	return session
}

type pushResponse struct {
	Results []struct {
		OperationID string `json:"operation_id"`
		Status      string `json:"status"`
		Version     int64  `json:"version"`
		Error       string `json:"error"`
	} `json:"results"`
}

func pushOperations(t *testing.T, handler http.Handler, token string, operations []map[string]any) pushResponse {
	t.Helper()
	body, err := json.Marshal(map[string]any{"operations": operations})
	if err != nil {
		t.Fatalf("marshal push request: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/api/v1/sync/push", bytes.NewReader(body))
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("expected push status 200, got %d: %s", response.Code, response.Body.String())
	}
	var result pushResponse
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		t.Fatalf("decode push response: %v", err)
	}
	return result
}

func readyMemberServer(t *testing.T, username string) (http.Handler, sessionResponse) {
	t.Helper()
	database, err := store.Open(filepath.Join(t.TempDir(), "planner.db"))
	if err != nil {
		t.Fatalf("open test store: %v", err)
	}
	t.Cleanup(func() { _ = database.Close() })
	if err := database.BootstrapAdmin(context.Background(), "owner", "owner-password"); err != nil {
		t.Fatalf("bootstrap admin: %v", err)
	}
	if err := database.CreateMember(context.Background(), username, "temporary-password", "America/Sao_Paulo"); err != nil {
		t.Fatalf("create member: %v", err)
	}
	server, err := httpapi.New(database, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("create HTTP server: %v", err)
	}
	handler := server.Router()
	session := login(t, handler, username, "temporary-password")
	changeRequest := httptest.NewRequest(http.MethodPost, "/api/v1/auth/change-password", bytes.NewBufferString(`{"password":"permanent-password"}`))
	changeRequest.Header.Set("Authorization", "Bearer "+session.Token)
	changeResponse := httptest.NewRecorder()
	handler.ServeHTTP(changeResponse, changeRequest)
	if changeResponse.Code != http.StatusOK {
		t.Fatalf("change member password: %d %s", changeResponse.Code, changeResponse.Body.String())
	}
	return handler, session
}

func assertAccountDisabled(t *testing.T, response *httptest.ResponseRecorder) {
	t.Helper()
	if response.Code != http.StatusForbidden {
		t.Fatalf("expected disabled status 403, got %d: %s", response.Code, response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"code":"account_disabled"`) {
		t.Fatalf("expected account_disabled code, got %s", response.Body.String())
	}
}

func postForm(handler http.Handler, target string, values url.Values) *httptest.ResponseRecorder {
	request := httptest.NewRequest(http.MethodPost, target, strings.NewReader(values.Encode()))
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

type sessionResponse struct {
	Token   string `json:"token"`
	Account struct {
		ID                 string `json:"id"`
		Username           string `json:"username"`
		Role               string `json:"role"`
		Timezone           string `json:"timezone"`
		MustChangePassword bool   `json:"must_change_password"`
	} `json:"account"`
	Planner struct {
		ID        string `json:"id"`
		AccountID string `json:"account_id"`
	} `json:"planner"`
}

func login(t *testing.T, handler http.Handler, username, password string) sessionResponse {
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
	var session sessionResponse
	if err := json.NewDecoder(response.Body).Decode(&session); err != nil {
		t.Fatalf("decode login response: %v", err)
	}
	return session
}

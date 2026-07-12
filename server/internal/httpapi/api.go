package httpapi

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"html/template"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"planner/server/internal/store"
)

//go:embed templates/*.html
var templateFS embed.FS

type contextKey string

const accountKey contextKey = "account"
const tokenKey contextKey = "token"

type Server struct {
	store     *store.Store
	templates *template.Template
	log       *slog.Logger
}

func New(store *store.Store, logger *slog.Logger) (*Server, error) {
	templates, err := template.ParseFS(templateFS, "templates/*.html")
	if err != nil {
		return nil, err
	}
	return &Server{store: store, templates: templates, log: logger}, nil
}

func (s *Server) Router() http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, middleware.Recoverer)
	r.Get("/healthz", s.health)
	r.Post("/api/v1/auth/login", s.login)
	r.Get("/admin/login", s.adminLoginPage)
	r.Post("/admin/login", s.adminLogin)
	r.Group(func(admin chi.Router) {
		admin.Use(s.adminAuthMiddleware)
		admin.Get("/admin/accounts", s.adminAccounts)
		admin.Post("/admin/accounts", s.adminCreateAccount)
		admin.Post("/admin/accounts/{id}/activate", s.adminActivateAccount)
		admin.Post("/admin/accounts/{id}/disable", s.adminDisableAccount)
		admin.Post("/admin/accounts/{id}/reset-password", s.adminResetPassword)
		admin.Post("/admin/logout", s.adminLogout)
	})

	r.Group(func(protected chi.Router) {
		protected.Use(s.authMiddleware)
		protected.Post("/api/v1/auth/change-password", s.changePassword)
		protected.Post("/api/v1/auth/logout", s.logout)
		protected.Group(func(ready chi.Router) {
			ready.Use(s.passwordChangedMiddleware)
			ready.Get("/api/v1/me", s.me)
			ready.Post("/api/v1/sync/push", s.syncPush)
			ready.Get("/api/v1/sync/pull", s.syncPull)
		})
	})
	return r
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) login(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if err := decodeJSON(r, &input); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	account, token, err := s.store.Authenticate(r.Context(), input.Username, input.Password)
	if errors.Is(err, store.ErrAccountDisabled) {
		writeCodedError(w, http.StatusForbidden, "account_disabled", "account is disabled")
		return
	}
	if errors.Is(err, store.ErrInvalidCredentials) {
		writeError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "login failed")
		return
	}
	planner, err := s.store.PlannerForAccount(r.Context(), account.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "planner bootstrap failed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"token": token, "account": account, "planner": planner})
}

func (s *Server) authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header := strings.TrimSpace(r.Header.Get("Authorization"))
		if !strings.HasPrefix(strings.ToLower(header), "bearer ") {
			writeError(w, http.StatusUnauthorized, "missing bearer token")
			return
		}
		raw := strings.TrimSpace(header[len("Bearer "):])
		account, err := s.store.AccountForToken(r.Context(), raw)
		if errors.Is(err, store.ErrAccountDisabled) {
			writeCodedError(w, http.StatusForbidden, "account_disabled", "account is disabled")
			return
		}
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}
		ctx := context.WithValue(r.Context(), accountKey, account)
		ctx = context.WithValue(ctx, tokenKey, raw)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func (s *Server) passwordChangedMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accountFromContext(r.Context()).MustChangePassword {
			writeCodedError(w, http.StatusForbidden, "password_change_required", "temporary password must be changed")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) changePassword(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Password string `json:"password"`
	}
	if err := decodeJSON(r, &input); err != nil || len(input.Password) < 8 {
		writeError(w, http.StatusBadRequest, "password must have at least 8 characters")
		return
	}
	account := accountFromContext(r.Context())
	if err := s.store.ChangePassword(r.Context(), account.ID, input.Password); err != nil {
		writeError(w, http.StatusInternalServerError, "password change failed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) logout(w http.ResponseWriter, r *http.Request) {
	if err := s.store.RevokeToken(r.Context(), tokenFromContext(r.Context())); err != nil {
		writeError(w, http.StatusInternalServerError, "logout failed")
		return
	}
	writeJSON(w, http.StatusNoContent, nil)
}

func (s *Server) me(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, accountFromContext(r.Context()))
}

func (s *Server) syncPush(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Operations []store.Operation `json:"operations"`
	}
	if err := decodeJSON(r, &input); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	account := accountFromContext(r.Context())
	results, err := s.store.ApplyOperations(r.Context(), account.ID, input.Operations)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "sync push failed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"results": results})
}

func (s *Server) syncPull(w http.ResponseWriter, r *http.Request) {
	cursor, err := strconv.ParseInt(r.URL.Query().Get("cursor"), 10, 64)
	if err != nil {
		cursor = 0
	}
	limit, err := strconv.Atoi(r.URL.Query().Get("limit"))
	if err != nil {
		limit = 100
	}
	account := accountFromContext(r.Context())
	changes, next, hasMore, err := s.store.PullChanges(r.Context(), account.ID, cursor, limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "sync pull failed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"next_cursor": next, "has_more": hasMore, "changes": changes})
}

func accountFromContext(ctx context.Context) store.Account {
	account, _ := ctx.Value(accountKey).(store.Account)
	return account
}

func tokenFromContext(ctx context.Context) string {
	token, _ := ctx.Value(tokenKey).(string)
	return token
}

func decodeJSON(r *http.Request, destination any) error {
	decoder := json.NewDecoder(http.MaxBytesReader(nil, r.Body, 1<<20))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		return errors.New("request body must contain a single JSON value")
	}
	return nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if value != nil {
		_ = json.NewEncoder(w).Encode(value)
	}
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func writeCodedError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]string{"code": code, "error": message})
}

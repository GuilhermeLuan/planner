package httpapi

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"planner/server/internal/store"
)

const adminSessionCookie = "planner_admin_session"

func (s *Server) adminLoginPage(w http.ResponseWriter, r *http.Request) {
	s.renderTemplate(w, http.StatusOK, "login", nil)
}

func (s *Server) adminLogin(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "formulário inválido", http.StatusBadRequest)
		return
	}
	account, token, err := s.store.Authenticate(r.Context(), r.FormValue("username"), r.FormValue("password"))
	if errors.Is(err, store.ErrAccountDisabled) {
		s.renderTemplate(w, http.StatusForbidden, "login", map[string]string{"Error": "Esta Conta está desativada"})
		return
	}
	if errors.Is(err, store.ErrInvalidCredentials) {
		s.renderTemplate(w, http.StatusUnauthorized, "login", map[string]string{"Error": "Credenciais inválidas"})
		return
	}
	if err != nil {
		http.Error(w, "não foi possível iniciar a sessão", http.StatusInternalServerError)
		return
	}
	if account.Role != "admin" {
		_ = s.store.RevokeToken(r.Context(), token)
		http.Error(w, "acesso restrito à Conta administradora", http.StatusForbidden)
		return
	}
	http.SetCookie(w, &http.Cookie{
		Name:     adminSessionCookie,
		Value:    token,
		Path:     "/admin",
		HttpOnly: true,
		Secure:   requestIsHTTPS(r),
		SameSite: http.SameSiteStrictMode,
		Expires:  time.Now().Add(30 * 24 * time.Hour),
	})
	http.Redirect(w, r, "/admin/accounts", http.StatusSeeOther)
}

func (s *Server) adminAuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie(adminSessionCookie)
		if err != nil {
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		account, err := s.store.AccountForToken(r.Context(), cookie.Value)
		if err != nil || account.Role != "admin" {
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		ctx := context.WithValue(r.Context(), accountKey, account)
		ctx = context.WithValue(ctx, tokenKey, cookie.Value)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func (s *Server) adminAccounts(w http.ResponseWriter, r *http.Request) {
	accounts, err := s.store.ListAccounts(r.Context())
	if err != nil {
		http.Error(w, "não foi possível listar as Contas", http.StatusInternalServerError)
		return
	}
	s.renderTemplate(w, http.StatusOK, "accounts", map[string]any{"Accounts": accounts})
}

func (s *Server) adminCreateAccount(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "formulário inválido", http.StatusBadRequest)
		return
	}
	username := r.FormValue("username")
	password := r.FormValue("password")
	timezone := r.FormValue("timezone")
	if username == "" || len(password) < 8 || timezone == "" {
		http.Error(w, "nome, senha temporária e fuso são obrigatórios", http.StatusBadRequest)
		return
	}
	if err := s.store.CreateMember(r.Context(), username, password, timezone); err != nil {
		if errors.Is(err, store.ErrInvalidTimezone) {
			http.Error(w, "fuso da Conta inválido", http.StatusBadRequest)
			return
		}
		if errors.Is(err, store.ErrDuplicate) {
			http.Error(w, "Uma Conta com esse nome já existe", http.StatusConflict)
			return
		}
		http.Error(w, "não foi possível criar a Conta", http.StatusInternalServerError)
		return
	}
	http.Redirect(w, r, "/admin/accounts", http.StatusSeeOther)
}

func (s *Server) adminActivateAccount(w http.ResponseWriter, r *http.Request) {
	s.setAccountStatus(w, r, "active")
}

func (s *Server) adminDisableAccount(w http.ResponseWriter, r *http.Request) {
	s.setAccountStatus(w, r, "disabled")
}

func (s *Server) setAccountStatus(w http.ResponseWriter, r *http.Request, status string) {
	if err := s.store.SetAccountStatus(r.Context(), chi.URLParam(r, "id"), status); err != nil {
		http.Error(w, "não foi possível alterar o estado da Conta", http.StatusInternalServerError)
		return
	}
	http.Redirect(w, r, "/admin/accounts", http.StatusSeeOther)
}

func (s *Server) adminResetPassword(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "formulário inválido", http.StatusBadRequest)
		return
	}
	password := r.FormValue("password")
	if len(password) < 8 {
		http.Error(w, "a senha temporária deve ter ao menos 8 caracteres", http.StatusBadRequest)
		return
	}
	if err := s.store.ResetPassword(r.Context(), chi.URLParam(r, "id"), password); err != nil {
		http.Error(w, "não foi possível redefinir a senha", http.StatusInternalServerError)
		return
	}
	http.Redirect(w, r, "/admin/accounts", http.StatusSeeOther)
}

func (s *Server) adminLogout(w http.ResponseWriter, r *http.Request) {
	if err := s.store.RevokeToken(r.Context(), tokenFromContext(r.Context())); err != nil {
		http.Error(w, "não foi possível encerrar a sessão", http.StatusInternalServerError)
		return
	}
	http.SetCookie(w, &http.Cookie{
		Name:     adminSessionCookie,
		Path:     "/admin",
		HttpOnly: true,
		Secure:   requestIsHTTPS(r),
		SameSite: http.SameSiteStrictMode,
		MaxAge:   -1,
		Expires:  time.Unix(1, 0),
	})
	http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
}

func requestIsHTTPS(r *http.Request) bool {
	return r.TLS != nil || strings.EqualFold(strings.TrimSpace(r.Header.Get("X-Forwarded-Proto")), "https")
}

func (s *Server) renderTemplate(w http.ResponseWriter, status int, name string, data any) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.templates.ExecuteTemplate(w, name, data); err != nil {
		s.log.Error("render admin template", "template", name, "error", err)
	}
}

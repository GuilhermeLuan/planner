package app

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"strings"

	"planner/server/internal/httpapi"
	"planner/server/internal/store"
)

type Config struct {
	DatabasePath  string
	AdminUsername string
	AdminPassword string
}

type App struct {
	store   *store.Store
	handler http.Handler
}

func Open(config Config, logger *slog.Logger) (*App, error) {
	if strings.TrimSpace(config.DatabasePath) == "" {
		return nil, errors.New("database path is required")
	}
	if strings.TrimSpace(config.AdminUsername) == "" || config.AdminPassword == "" {
		return nil, errors.New("admin username and password are required")
	}
	database, err := store.Open(config.DatabasePath)
	if err != nil {
		return nil, err
	}
	if err := database.BootstrapAdmin(context.Background(), config.AdminUsername, config.AdminPassword); err != nil {
		_ = database.Close()
		return nil, err
	}
	server, err := httpapi.New(database, logger)
	if err != nil {
		_ = database.Close()
		return nil, err
	}
	return &App{store: database, handler: server.Router()}, nil
}

func (a *App) Handler() http.Handler { return a.handler }

func (a *App) Close() error { return a.store.Close() }

package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"planner/server/internal/app"
)

type configuration struct {
	HTTPAddress   string
	DatabasePath  string
	AdminUsername string
	AdminPassword string
}

func configFromEnvironment() (configuration, error) {
	config := configuration{
		HTTPAddress:   environmentOrDefault("PLANNER_HTTP_ADDR", ":8080"),
		DatabasePath:  environmentOrDefault("PLANNER_DB_PATH", "/data/planner.db"),
		AdminUsername: strings.TrimSpace(os.Getenv("PLANNER_ADMIN_USERNAME")),
		AdminPassword: os.Getenv("PLANNER_ADMIN_PASSWORD"),
	}
	if config.AdminUsername == "" || config.AdminPassword == "" {
		return configuration{}, errors.New("PLANNER_ADMIN_USERNAME and PLANNER_ADMIN_PASSWORD are required")
	}
	return config, nil
}

func environmentOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if err := run(logger); err != nil {
		logger.Error("planner server stopped", "error", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	config, err := configFromEnvironment()
	if err != nil {
		return err
	}
	planner, err := app.Open(app.Config{
		DatabasePath:  config.DatabasePath,
		AdminUsername: config.AdminUsername,
		AdminPassword: config.AdminPassword,
	}, logger)
	if err != nil {
		return fmt.Errorf("open planner: %w", err)
	}
	defer planner.Close()

	server := &http.Server{
		Addr:              config.HTTPAddress,
		Handler:           planner.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	stopped := make(chan os.Signal, 1)
	signal.Notify(stopped, syscall.SIGINT, syscall.SIGTERM)
	defer signal.Stop(stopped)

	serveResult := make(chan error, 1)
	go func() { serveResult <- server.ListenAndServe() }()
	logger.Info("planner server listening", "address", config.HTTPAddress)

	select {
	case err := <-serveResult:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-stopped:
		shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		return server.Shutdown(shutdownContext)
	}
}

package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"

	"planner/server/internal/backup"
)

func configFromEnvironment() (backup.Config, error) {
	retention, err := strconv.Atoi(environmentOrDefault("PLANNER_BACKUP_RETENTION_DAYS", "14"))
	if err != nil || retention < 1 {
		return backup.Config{}, fmt.Errorf("PLANNER_BACKUP_RETENTION_DAYS must be a positive integer")
	}
	return backup.Config{
		DatabasePath:   environmentOrDefault("PLANNER_DB_PATH", "/data/planner.db"),
		DestinationDir: environmentOrDefault("PLANNER_BACKUP_DIR", "/backups"),
		RetentionDays:  retention,
	}, nil
}

func environmentOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func main() {
	config, err := configFromEnvironment()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	result, err := backup.Create(ctx, config)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Printf("Backup criado: %s (expirados removidos: %d)\n", result.Path, result.RemovedFiles)
}

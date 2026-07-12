package backup

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

type Config struct {
	DatabasePath   string
	DestinationDir string
	RetentionDays  int
}

type Result struct {
	Path         string
	RemovedFiles int
}

func Create(ctx context.Context, config Config) (Result, error) {
	if strings.TrimSpace(config.DatabasePath) == "" || strings.TrimSpace(config.DestinationDir) == "" {
		return Result{}, errors.New("database path and destination directory are required")
	}
	if config.RetentionDays < 1 {
		return Result{}, errors.New("retention days must be at least one")
	}
	if err := os.MkdirAll(config.DestinationDir, 0o750); err != nil {
		return Result{}, fmt.Errorf("create backup directory: %w", err)
	}

	stamp := time.Now().UTC().Format("20060102T150405.000000000Z")
	destination := filepath.Join(config.DestinationDir, "planner-"+stamp+".db")
	database, err := sql.Open("sqlite", config.DatabasePath)
	if err != nil {
		return Result{}, fmt.Errorf("open source database: %w", err)
	}
	defer database.Close()
	database.SetMaxOpenConns(1)
	if _, err := database.ExecContext(ctx, `VACUUM INTO ?`, destination); err != nil {
		return Result{}, fmt.Errorf("create consistent SQLite backup: %w", err)
	}

	backupDatabase, err := sql.Open("sqlite", destination)
	if err != nil {
		return Result{}, fmt.Errorf("open backup for verification: %w", err)
	}
	var integrity string
	checkErr := backupDatabase.QueryRowContext(ctx, `PRAGMA integrity_check`).Scan(&integrity)
	closeErr := backupDatabase.Close()
	if checkErr != nil {
		return Result{}, fmt.Errorf("verify backup: %w", checkErr)
	}
	if closeErr != nil {
		return Result{}, fmt.Errorf("close verified backup: %w", closeErr)
	}
	if integrity != "ok" {
		return Result{}, fmt.Errorf("backup integrity check returned %q", integrity)
	}

	removed, err := removeExpired(config.DestinationDir, destination, time.Now().Add(-time.Duration(config.RetentionDays)*24*time.Hour))
	if err != nil {
		return Result{}, err
	}
	return Result{Path: destination, RemovedFiles: removed}, nil
}

func removeExpired(directory, current string, cutoff time.Time) (int, error) {
	entries, err := os.ReadDir(directory)
	if err != nil {
		return 0, fmt.Errorf("read backup directory: %w", err)
	}
	removed := 0
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasPrefix(entry.Name(), "planner-") || !strings.HasSuffix(entry.Name(), ".db") {
			continue
		}
		path := filepath.Join(directory, entry.Name())
		if path == current {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			return removed, fmt.Errorf("inspect backup %s: %w", entry.Name(), err)
		}
		if info.ModTime().Before(cutoff) {
			if err := os.Remove(path); err != nil {
				return removed, fmt.Errorf("remove expired backup %s: %w", entry.Name(), err)
			}
			removed++
		}
	}
	return removed, nil
}

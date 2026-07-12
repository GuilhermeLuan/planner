package main

import "testing"

func TestBackupConfigurationComesFromEnvironment(t *testing.T) {
	t.Setenv("PLANNER_DB_PATH", "/data/custom.db")
	t.Setenv("PLANNER_BACKUP_DIR", "/backups/custom")
	t.Setenv("PLANNER_BACKUP_RETENTION_DAYS", "30")

	config, err := configFromEnvironment()
	if err != nil {
		t.Fatalf("load backup configuration: %v", err)
	}
	if config.DatabasePath != "/data/custom.db" || config.DestinationDir != "/backups/custom" || config.RetentionDays != 30 {
		t.Fatalf("unexpected backup configuration: %+v", config)
	}
}

func TestBackupConfigurationRejectsInvalidRetention(t *testing.T) {
	t.Setenv("PLANNER_BACKUP_RETENTION_DAYS", "zero")
	if _, err := configFromEnvironment(); err == nil {
		t.Fatal("expected invalid retention to be rejected")
	}
}

package main

import "testing"

func TestConfigurationComesFromInstallationEnvironment(t *testing.T) {
	t.Setenv("PLANNER_HTTP_ADDR", "127.0.0.1:9090")
	t.Setenv("PLANNER_DB_PATH", "/tmp/planner-test.db")
	t.Setenv("PLANNER_ADMIN_USERNAME", "owner")
	t.Setenv("PLANNER_ADMIN_PASSWORD", "a-secure-password")

	config, err := configFromEnvironment()
	if err != nil {
		t.Fatalf("load configuration: %v", err)
	}
	if config.HTTPAddress != "127.0.0.1:9090" {
		t.Fatalf("unexpected HTTP address: %q", config.HTTPAddress)
	}
	if config.DatabasePath != "/tmp/planner-test.db" {
		t.Fatalf("unexpected database path: %q", config.DatabasePath)
	}
	if config.AdminUsername != "owner" || config.AdminPassword != "a-secure-password" {
		t.Fatalf("unexpected admin credentials: %+v", config)
	}
}

func TestConfigurationRejectsMissingAdminCredentials(t *testing.T) {
	t.Setenv("PLANNER_ADMIN_USERNAME", "")
	t.Setenv("PLANNER_ADMIN_PASSWORD", "")

	if _, err := configFromEnvironment(); err == nil {
		t.Fatal("expected missing admin credentials to be rejected")
	}
}

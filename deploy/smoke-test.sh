#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
PROJECT_NAME="planner-smoke-$$"
PLANNER_PORT="${PLANNER_SMOKE_PORT:-18080}"
BASE_URL="http://127.0.0.1:$PLANNER_PORT"
PLANNER_BACKUP_HOST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/planner-smoke-backups.XXXXXX")"

cleanup() {
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --volumes --remove-orphans >/dev/null 2>&1 || true
  find "$PLANNER_BACKUP_HOST_DIR" -type f -delete >/dev/null 2>&1 || true
  rmdir "$PLANNER_BACKUP_HOST_DIR" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

export PLANNER_PORT
export PLANNER_BACKUP_HOST_DIR
export PLANNER_BACKUP_RETENTION_DAYS=14
export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export PLANNER_ADMIN_USERNAME="smoke-owner"
export PLANNER_ADMIN_PASSWORD="smoke-owner-password"

docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --build --detach --wait

health_status="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/healthz")"
test "$health_status" = "200"

login_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke-owner","password":"smoke-owner-password"}' \
  "$BASE_URL/api/v1/auth/login")"
test "$login_status" = "200"

admin_cookie_jar="$PLANNER_BACKUP_HOST_DIR/admin-cookie"
admin_login_status="$(curl -sS -o /dev/null -w '%{http_code}' -c "$admin_cookie_jar" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=smoke-owner&password=smoke-owner-password' \
  "$BASE_URL/admin/login")"
test "$admin_login_status" = "303"

account_create_status="$(curl -sS -o /dev/null -w '%{http_code}' -b "$admin_cookie_jar" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=smoke-member&password=temporary-password&timezone=America%2FSao_Paulo' \
  "$BASE_URL/admin/accounts")"
test "$account_create_status" = "303"

export PLANNER_ADMIN_USERNAME="replacement-owner"
export PLANNER_ADMIN_PASSWORD="replacement-owner-password"
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --detach --force-recreate --wait

persisted_login_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke-owner","password":"smoke-owner-password"}' \
  "$BASE_URL/api/v1/auth/login")"
test "$persisted_login_status" = "200"

replacement_login_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"replacement-owner","password":"replacement-owner-password"}' \
  "$BASE_URL/api/v1/auth/login")"
test "$replacement_login_status" = "401"

session_body="$(curl -sS \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke-owner","password":"smoke-owner-password"}' \
  "$BASE_URL/api/v1/auth/login")"
session_token="$(printf '%s' "$session_body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
test -n "$session_token"

curl -sS -o /dev/null -f \
  -H "Authorization: Bearer $session_token" \
  -H 'Content-Type: application/json' \
  -d '{"operations":[{"operation_id":"backup-smoke-operation","entity_type":"task","entity_id":"backup-smoke-task","kind":"upsert","payload":{"title":"Presente no backup"}}]}' \
  "$BASE_URL/api/v1/sync/push"

"$SCRIPT_DIR/backup.sh" >/dev/null
backup_path="$(find "$PLANNER_BACKUP_HOST_DIR" -type f -name 'planner-*.db' -print | sort | head -n 1)"
test -n "$backup_path"
backup_name="$(basename "$backup_path")"

curl -sS -o /dev/null -f \
  -H "Authorization: Bearer $session_token" \
  -H 'Content-Type: application/json' \
  -d '{"operations":[{"operation_id":"after-backup-operation","entity_type":"task","entity_id":"after-backup-task","kind":"upsert","payload":{"title":"Não deve sobreviver à restauração"}}]}' \
  "$BASE_URL/api/v1/sync/push"

PLANNER_RESTORE_CONFIRM=yes "$SCRIPT_DIR/restore.sh" "$backup_name" >/dev/null

restored_session_body="$(curl -sS \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke-owner","password":"smoke-owner-password"}' \
  "$BASE_URL/api/v1/auth/login")"
restored_token="$(printf '%s' "$restored_session_body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
test -n "$restored_token"
restored_changes="$(curl -sS -f -H "Authorization: Bearer $restored_token" "$BASE_URL/api/v1/sync/pull?cursor=0&limit=100")"
printf '%s' "$restored_changes" | grep -q 'backup-smoke-task'
if printf '%s' "$restored_changes" | grep -q 'after-backup-task'; then
  printf 'A restauração manteve dados criados depois do backup.\n' >&2
  exit 1
fi

printf 'Smoke test concluído: health, bootstrap, volume, backup e restauração.\n'

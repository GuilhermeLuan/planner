#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  printf 'Uso: PLANNER_RESTORE_CONFIRM=yes %s <arquivo-do-backup.db>\n' "$0" >&2
  exit 2
fi
if [ "${PLANNER_RESTORE_CONFIRM:-}" != "yes" ]; then
  printf 'Restauração cancelada. Defina PLANNER_RESTORE_CONFIRM=yes para confirmar.\n' >&2
  exit 2
fi

BACKUP_NAME="$1"
case "$BACKUP_NAME" in
  */*|..|.|"") printf 'Informe somente o nome de um arquivo dentro da pasta configurada de backups.\n' >&2; exit 2 ;;
esac

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$SCRIPT_DIR"

./backup.sh
docker compose -f docker-compose.yml stop planner-server

restart_service() {
  docker compose -f docker-compose.yml up --detach --wait planner-server >/dev/null 2>&1 || true
}
trap restart_service EXIT INT TERM

docker compose -f docker-compose.yml run --rm --no-deps \
  -e "PLANNER_RESTORE_FILE=$BACKUP_NAME" \
  --entrypoint sh planner-server \
  -c 'test -f "/backups/$PLANNER_RESTORE_FILE" && cp "/backups/$PLANNER_RESTORE_FILE" /data/planner.db'

docker compose -f docker-compose.yml up --detach --wait planner-server
trap - EXIT INT TERM
printf 'Restauração concluída a partir de: %s\n' "$BACKUP_NAME"

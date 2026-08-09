#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

BACKUP_DIR=""
EXECUTE=0
CONFIRM=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    --confirm) CONFIRM="$2"; shift 2 ;;
    -h|--help) printf '%s\n' 'Usage: restore.sh --backup-dir DIR [--execute --confirm RESTORE-structify.cn]'; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done
[[ -n "$BACKUP_DIR" && -d "$BACKUP_DIR" ]] || die "--backup-dir must be an existing backup directory"
[[ -r "$BACKUP_DIR/SHA256SUMS" ]] || die "backup is missing SHA256SUMS"
[[ -r "$BACKUP_DIR/mysql.sql" && -r "$BACKUP_DIR/node.sqlite" ]] || die "backup is missing database artifacts"

if [[ "$EXECUTE" != "1" ]]; then
  log "dry-run restore plan for $BACKUP_DIR"
  print_command '(cd BACKUP_DIR && sha256sum -c SHA256SUMS)'
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop node spring-api
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql mysql restore from mysql.sql
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" run --rm --no-deps node restore node.sqlite into /app/data
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d node spring-api caddy
  log "database restore replaces current state and requires --execute --confirm RESTORE-structify.cn"
  exit 0
fi

[[ "$CONFIRM" == "RESTORE-structify.cn" ]] || die "restore requires --confirm RESTORE-structify.cn"
require_command docker
(cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS)
compose stop node spring-api
compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < "$BACKUP_DIR/mysql.sql"
compose run --rm --no-deps --entrypoint /bin/sh -v "$BACKUP_DIR:/restore:ro" node -c 'cp /restore/node.sqlite /app/data/data.db && rm -f /app/data/data.db-wal /app/data/data.db-shm'
if [[ -r "$BACKUP_DIR/node-pdfs.tar.gz" ]]; then
  compose run --rm --no-deps --entrypoint /bin/sh -v "$BACKUP_DIR:/restore:ro" node -c 'rm -rf /app/pdfs/* /app/pdfs/.[!.]* 2>/dev/null || true; tar -xzf /restore/node-pdfs.tar.gz -C /app/pdfs'
fi
compose up -d node spring-api caddy
"$SCRIPT_DIR/health-check.sh" --env-file "$ENV_FILE" --execute
log "database restore complete; private media must be restored from its independent snapshot"

#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

ENV_FILE="${ENV_FILE:-/etc/structify/structify.env}"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/structify}"
PRIVATE_ROOT=""
EXECUTE=0
CONFIRM=""
RETAIN=2

usage() {
  cat <<'EOF'
Usage: backup.sh [--env-file FILE] [--backup-root DIR] [--private-root DIR]
                  [--retain COUNT] [--execute --confirm BACKUP-structify.cn]

Default mode prints the exact backup plan and does not contact Docker. The
execute mode creates a 0700 directory containing a MySQL dump, a consistent
Node SQLite backup, optional private course media, image metadata, and hashes.
Secrets are never printed or copied into the repository.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --backup-root) BACKUP_ROOT="$2"; shift 2 ;;
    --private-root) PRIVATE_ROOT="$2"; shift 2 ;;
    --retain) RETAIN="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    --confirm) CONFIRM="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done

[[ "$RETAIN" =~ ^[0-9]+$ ]] || die "--retain must be a whole number"
(( 10#$RETAIN >= 2 )) || die "--retain must be at least 2 to preserve rollback"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$BACKUP_ROOT/$STAMP"

if [[ "$EXECUTE" != "1" ]]; then
  log "dry-run backup plan"
  print_command mkdir -m 700 -p "$DEST"
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql mysqldump --single-transaction --routines --events
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T node node -e "SQLite online backup to stdout"
  [[ -n "$PRIVATE_ROOT" ]] && print_command tar -C "$PRIVATE_ROOT" -czf "$DEST/private.tar.gz" .
  log "retention: keep the newest $((10#$RETAIN)) completed backup directories"
  log "re-run with --execute --confirm BACKUP-structify.cn after checking paths"
  exit 0
fi

[[ "$CONFIRM" == "BACKUP-structify.cn" ]] || die "backup requires --confirm BACKUP-structify.cn"
require_command docker
[[ -n "$PRIVATE_ROOT" ]] || warn "private media was not requested; restore requires an independent media snapshot"
[[ ! -e "$DEST" ]] || die "backup destination already exists: $DEST"
mkdir -m 700 -p "$DEST"
chmod 700 "$DEST"
compose config --quiet

compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --events --no-tablespaces --set-gtid-purged=OFF -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > "$DEST/mysql.sql"
compose exec -T node node -e '
const fs = require("node:fs");
const Database = require("better-sqlite3");
const source = process.env.DB_PATH;
const target = "/tmp/structify-node-backup.sqlite";
const db = new Database(source, { readonly: true, fileMustExist: true });
db.backup(target).then(() => {
  db.close();
  process.stdout.write(fs.readFileSync(target));
  fs.rmSync(target, { force: true });
}).catch((error) => { try { db.close(); } catch {} process.stderr.write(error.stack || String(error)); process.exit(1); });
' > "$DEST/node.sqlite"
compose exec -T node tar -czf - -C /app/pdfs . > "$DEST/node-pdfs.tar.gz"

if [[ -n "$PRIVATE_ROOT" ]]; then
  [[ -d "$PRIVATE_ROOT" ]] || die "private root is not a directory: $PRIVATE_ROOT"
  tar -C "$PRIVATE_ROOT" -czf "$DEST/private.tar.gz" .
fi

printf 'created_at=%s\n' "$STAMP" > "$DEST/manifest.txt"
printf 'compose_file=%s\n' "$COMPOSE_FILE" >> "$DEST/manifest.txt"
for service in node spring-api mysql; do
  container="$(compose ps -q "$service" 2>/dev/null || true)"
  if [[ -n "$container" ]]; then
    docker inspect --format "service=$service image={{.Config.Image}} image_id={{.Image}}" "$container" >> "$DEST/manifest.txt"
  fi
done
(cd "$DEST" && sha256sum -- * > SHA256SUMS)
chmod 600 "$DEST"/*
log "backup complete: $DEST"

# Keep only the newest completed backup directories. The current backup is
# complete before pruning starts, and non-timestamp control files remain.
mapfile -t backup_dirs < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d \
  -regextype posix-extended -regex '.*/20[0-9]{6}T[0-9]{6}Z' -printf '%f\n' | sort -r)
if (( ${#backup_dirs[@]} > 10#$RETAIN )); then
  for (( index=10#$RETAIN; index<${#backup_dirs[@]}; index++ )); do
    candidate="${BACKUP_ROOT}/${backup_dirs[$index]}"
    candidate_real="$(realpath -e "$candidate")"
    backup_root_real="$(realpath -e "$BACKUP_ROOT")"
    [[ "$candidate_real" == "$backup_root_real"/* ]] || die "refusing to prune outside backup root"
    log "pruning obsolete backup ${backup_dirs[$index]}"
    rm -rf -- "$candidate_real"
  done
fi

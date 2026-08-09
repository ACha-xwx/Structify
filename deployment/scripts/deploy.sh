#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

EXECUTE=0
CONFIRM=""
RELEASE=""
PRIVATE_ROOT=""
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/structify}"

usage() {
  cat <<'EOF'
Usage: deploy.sh --release RELEASE [--env-file FILE] [--private-root DIR]
                 [--execute --confirm DEPLOY-structify.cn]

Default mode validates the release name and prints the build/backup/migration
plan. Execute mode builds immutable local Node/Spring images, captures a backup,
starts MySQL, lets Spring run Flyway migrations, then starts both APIs and Caddy.
DNS is never changed by this script.
EOF
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) RELEASE="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --private-root) PRIVATE_ROOT="$2"; shift 2 ;;
    --backup-root) BACKUP_ROOT="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    --confirm) CONFIRM="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done
[[ -n "$RELEASE" ]] || die "--release is required"
safe_release_tag "$RELEASE"
require_command docker

configured_node_image="$(env_value NODE_IMAGE)"
configured_spring_image="$(env_value SPRING_IMAGE)"
[[ "$configured_node_image" == "structify-node:$RELEASE" ]] || die "NODE_IMAGE must be structify-node:$RELEASE in the environment file"
[[ "$configured_spring_image" == "structify-spring:$RELEASE" ]] || die "SPRING_IMAGE must be structify-spring:$RELEASE in the environment file"

if [[ "$EXECUTE" != "1" ]]; then
  log "dry-run deploy plan for release $RELEASE"
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build node spring-api
  print_command "$SCRIPT_DIR/backup.sh" --env-file "$ENV_FILE" --backup-root "$BACKUP_ROOT" --private-root "${PRIVATE_ROOT:-/srv/structify/private}" --execute --confirm BACKUP-structify.cn
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d mysql
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d node spring-api caddy
  log "re-run with --execute --confirm DEPLOY-structify.cn after review"
  exit 0
fi

[[ "$CONFIRM" == "DEPLOY-structify.cn" ]] || die "deploy requires --confirm DEPLOY-structify.cn"
[[ -n "$PRIVATE_ROOT" ]] || PRIVATE_ROOT="/srv/structify/private"
require_command curl
"$SCRIPT_DIR/preflight.sh" --env-file "$ENV_FILE" --compose-file "$COMPOSE_FILE" --execute

mkdir -m 700 -p "$BACKUP_ROOT"
if [[ -r "$BACKUP_ROOT/last-release.env" ]]; then
  cp -p "$BACKUP_ROOT/last-release.env" "$BACKUP_ROOT/previous-release.env"
  chmod 600 "$BACKUP_ROOT/previous-release.env"
fi

compose build --pull=false node spring-api

"$SCRIPT_DIR/backup.sh" --env-file "$ENV_FILE" --backup-root "$BACKUP_ROOT" --private-root "$PRIVATE_ROOT" --execute --confirm BACKUP-structify.cn

compose up -d mysql
compose up -d node spring-api
compose up -d caddy

for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 5 http://127.0.0.1:8791/healthz >/dev/null 2>&1 \
    && curl --fail --silent --max-time 5 http://127.0.0.1:8792/actuator/health >/dev/null 2>&1; then
    break
  fi
  [[ "$attempt" -lt 30 ]] || die "services did not become healthy; inspect compose logs"
  sleep 2
done

node_container="$(compose ps -q node)"
spring_container="$(compose ps -q spring-api)"
[[ -n "$node_container" && -n "$spring_container" ]] || die "cannot record release: application container is missing"
node_image_id="$(docker inspect --format '{{.Image}}' "$node_container")"
spring_image_id="$(docker inspect --format '{{.Image}}' "$spring_container")"
printf 'NODE_IMAGE=%s\nSPRING_IMAGE=%s\nNODE_IMAGE_ID=%s\nSPRING_IMAGE_ID=%s\n' \
  "$configured_node_image" "$configured_spring_image" "$node_image_id" "$spring_image_id" > "$BACKUP_ROOT/last-release.env"
chmod 600 "$BACKUP_ROOT/last-release.env"
log "release $RELEASE is running; DNS remains unchanged"

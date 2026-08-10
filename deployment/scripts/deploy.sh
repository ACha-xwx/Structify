#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

EXECUTE=0
SKIP_BUILD=0
CONFIRM=""
RELEASE=""
PRIVATE_ROOT=""
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/structify}"

usage() {
  cat <<'EOF'
Usage: deploy.sh --release RELEASE [--env-file FILE] [--private-root DIR]
                 [--skip-build] [--execute --confirm DEPLOY-structify.cn]

Default mode validates the release name and prints the build/backup/migration
plan. --skip-build requires the immutable Node/Spring release images to already
exist locally. Execute mode otherwise builds those images, captures a backup,
starts MySQL, and lets Spring run Flyway migrations. In host Caddy mode it never
touches public 80/443; the host operator installs and reloads the reviewed site
block separately. DNS is never changed by this script.
EOF
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) RELEASE="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --private-root) PRIVATE_ROOT="$2"; shift 2 ;;
    --backup-root) BACKUP_ROOT="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
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
caddy_mode_value="$(caddy_mode)"
node_port="$(node_host_port)"
spring_port="$(spring_host_port)"

bootstrap_data_services() {
  local running_mysql running_node
  running_mysql="$(compose ps --status running -q mysql 2>/dev/null || true)"
  running_node="$(compose ps --status running -q node 2>/dev/null || true)"

  if [[ -n "$running_mysql" && -n "$running_node" ]]; then
    log "existing data services are running; capturing a pre-release backup"
    return
  fi
  if [[ -n "$running_mysql" || -n "$running_node" ]]; then
    die "data services are only partially running; inspect and recover them before deployment"
  fi

  log "bootstrap data services before the first Flyway migration"
  compose up -d --no-build mysql node
  for attempt in $(seq 1 30); do
    if compose exec -T mysql mysqladmin ping -h 127.0.0.1 --silent >/dev/null 2>&1 \
      && curl --fail --silent --max-time 5 "http://127.0.0.1:$node_port/healthz" >/dev/null 2>&1; then
      return
    fi
    [[ "$attempt" -lt 30 ]] || die "bootstrap data services did not become healthy; inspect compose logs"
    sleep 2
  done
}

verify_release_images() {
  docker image inspect "$configured_node_image" >/dev/null 2>&1 \
    || die "NODE_IMAGE is not available locally for --skip-build: $configured_node_image"
  docker image inspect "$configured_spring_image" >/dev/null 2>&1 \
    || die "SPRING_IMAGE is not available locally for --skip-build: $configured_spring_image"
}

if [[ "$EXECUTE" != "1" ]]; then
  log "dry-run deploy plan for release $RELEASE"
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log "--skip-build: verify immutable release images before deployment"
    print_command docker image inspect "$configured_node_image"
    print_command docker image inspect "$configured_spring_image"
  else
    print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build node spring-api
  fi
  log "if no Node/MySQL containers are running, bootstrap data services before the persistent-data backup"
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-build mysql node
  print_command "$SCRIPT_DIR/backup.sh" --env-file "$ENV_FILE" --backup-root "$BACKUP_ROOT" --private-root "${PRIVATE_ROOT:-/srv/structify/private}" --execute --confirm BACKUP-structify.cn
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-build node spring-api
  if [[ "$caddy_mode_value" == "container" ]]; then
    print_command docker compose --profile container-caddy --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-build caddy
  else
    log "host Caddy mode: validate and reload the existing host configuration after the application health checks"
  fi
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

if [[ "$SKIP_BUILD" == "1" ]]; then
  verify_release_images
  log "--skip-build: using verified immutable local release images"
else
  compose build --pull=false node spring-api
fi

bootstrap_data_services
"$SCRIPT_DIR/backup.sh" --env-file "$ENV_FILE" --backup-root "$BACKUP_ROOT" --private-root "$PRIVATE_ROOT" --execute --confirm BACKUP-structify.cn

compose up -d --no-build node spring-api
if [[ "$caddy_mode_value" == "container" ]]; then
  docker compose --profile container-caddy --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-build caddy
else
  log "host Caddy mode: application services are ready on loopback ports $node_port/$spring_port; no public listener was changed"
fi

for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 5 "http://127.0.0.1:$node_port/healthz" >/dev/null 2>&1 \
    && curl --fail --silent --max-time 5 "http://127.0.0.1:$spring_port/actuator/health" >/dev/null 2>&1; then
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

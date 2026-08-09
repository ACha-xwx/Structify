#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

usage() {
  cat <<'EOF'
Usage: preflight.sh [--env-file FILE] [--compose-file FILE] [--execute]

Read-only by default. --execute additionally requires the private host paths
to exist and checks that Docker can render the production Compose model.
EOF
}

EXECUTE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done

[[ -r "$ENV_FILE" ]] || die "environment file is not readable: $ENV_FILE"
if [[ "$EXECUTE" == "1" ]]; then
  [[ ! -L "$ENV_FILE" ]] || die "environment file must not be a symlink: $ENV_FILE"
  env_mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || true)"
  [[ "$env_mode" == "600" ]] || die "environment file must have mode 0600 (got ${env_mode:-unknown})"
fi
mode="$(caddy_mode)"
node_port="$(node_host_port)"
spring_port="$(spring_host_port)"
[[ "$node_port" != "$spring_port" ]] || die "NODE_HOST_PORT and SPRING_HOST_PORT must differ"

required=(MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET NODE_COMPAT_JWT_SECRET KNOWLEDGE_DIR_HOST RESOURCE_DIR_HOST PRESENTATION_DIR_HOST NODE_IMAGE SPRING_IMAGE)
for key in "${required[@]}"; do
  value="$(env_value "$key")"
  [[ -n "$value" ]] || die "$key is empty"
  [[ "$value" != __*__ ]] || die "$key still contains a placeholder"
done

# Model, mail, and remote execution are runtime integrations. The application
# may start without them and report a precise unavailable capability; when an
# integration is enabled, all of its required fields must be present.
model_key="$(env_value MODEL_API_KEY)"
if [[ -n "$model_key" ]]; then
  for key in MODEL_PROVIDER MODEL_BASE_URL MODEL_NAME; do
    value="$(env_value "$key")"
    [[ -n "$value" ]] || die "$key is required when MODEL_API_KEY is configured"
    [[ "$value" != __*__ ]] || die "$key still contains a placeholder"
  done
else
  log "model integration disabled (MODEL_API_KEY is empty)"
fi

if [[ "$mode" == "host" ]]; then
  [[ -f "$DEPLOY_DIR/Caddyfile.host.production" ]] || die "host Caddy site block is missing"
  log "host Caddy mode: Structify will not bind public 80/443"
else
  acme_email="$(env_value ACME_EMAIL)"
  [[ -n "$acme_email" && "$acme_email" != __*__ ]] || die "ACME_EMAIL is required when CADDY_MODE=container"
  log "container Caddy mode: Structify owns public 80/443"
fi

mail_enabled="$(env_value AUTH_MAIL_ENABLED)"
mail_enabled="${mail_enabled:-false}"
if [[ "$mail_enabled" =~ ^(true|1|yes|on)$ ]]; then
  for key in SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASS SMTP_FROM; do
    value="$(env_value "$key")"
    [[ -n "$value" ]] || die "$key is required when AUTH_MAIL_ENABLED is true"
    [[ "$value" != __*__ ]] || die "$key still contains a placeholder"
  done
else
  log "mail integration disabled (AUTH_MAIL_ENABLED is false)"
fi

for key in JUDGE0_BASE_URL PISTON_BASE_URL; do
  value="$(env_value "$key")"
  if [[ -n "$value" ]]; then
    [[ "$value" != __*__ ]] || die "$key still contains a placeholder"
    [[ "$value" =~ ^https:// ]] || die "$key must use HTTPS"
  fi
done

[[ "$(env_value CORS_ALLOWED_ORIGINS)" == "https://structify.cn" ]] || die "CORS_ALLOWED_ORIGINS must be exactly https://structify.cn"
[[ -z "$(env_value BOOTSTRAP_ADMIN_EMAIL)" ]] || die "BOOTSTRAP_ADMIN_EMAIL must be empty in production"
[[ -z "$(env_value TEACHER_EMAILS)" ]] || die "TEACHER_EMAILS must be empty in production"
[[ "$(env_value ALLOW_FIRST_USER_TEACHER)" =~ ^(false|0|no|off)$ ]] || die "ALLOW_FIRST_USER_TEACHER must be false"
[[ "$(env_value JWT_SECRET)" =~ ^.{64,}$ ]] || die "JWT_SECRET must be at least 64 characters"
[[ "$(env_value NODE_COMPAT_JWT_SECRET)" =~ ^.{64,}$ ]] || die "NODE_COMPAT_JWT_SECRET must be at least 64 characters"
[[ "$(env_value JWT_SECRET)" != "$(env_value NODE_COMPAT_JWT_SECRET)" ]] || die "JWT_SECRET and NODE_COMPAT_JWT_SECRET must differ"
node_compat_enabled="$(env_value NODE_COMPAT_ENABLED)"
node_compat_enabled="${node_compat_enabled:-true}"
[[ "$node_compat_enabled" =~ ^(true|false|1|0|yes|no|on|off)$ ]] || die "NODE_COMPAT_ENABLED must be boolean"
[[ "$(env_value AUTH_COOKIE_SECURE)" != "false" ]] || die "AUTH_COOKIE_SECURE must not be false"
[[ "$(env_value AUTH_EXPOSE_DEV_CODE)" =~ ^(false|0|no|off)$ ]] || die "AUTH_EXPOSE_DEV_CODE must be false"

[[ -z "$(env_value VERIFICATION_CODE_FILE)" ]] || die "VERIFICATION_CODE_FILE must be empty in production"
[[ "$(env_value KNOWLEDGE_DEBUG_API)" =~ ^(false|0|no|off)$ ]] || die "KNOWLEDGE_DEBUG_API must be false"

for path_key in KNOWLEDGE_DIR_HOST RESOURCE_DIR_HOST PRESENTATION_DIR_HOST; do
  path_value="$(env_value "$path_key")"
  [[ "$path_value" == /* ]] || die "$path_key must be an absolute Linux path"
  if [[ "$EXECUTE" == "1" && ! -d "$path_value" ]]; then
    die "$path_key does not exist: $path_value"
  fi
done

require_command docker
[[ -f "$COMPOSE_FILE" ]] || die "Compose file is missing: $COMPOSE_FILE"
if [[ "$EXECUTE" == "1" ]]; then
  compose config --quiet
else
  log "dry-run: Docker Compose rendering was not executed"
  print_command docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
fi

log "preflight passed (no network or server connection was made)"

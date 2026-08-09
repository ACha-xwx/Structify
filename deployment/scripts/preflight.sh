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
required=(ACME_EMAIL MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET MODEL_PROVIDER MODEL_API_KEY MODEL_BASE_URL MODEL_NAME SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASS SMTP_FROM PISTON_BASE_URL KNOWLEDGE_DIR_HOST RESOURCE_DIR_HOST PRESENTATION_DIR_HOST NODE_IMAGE SPRING_IMAGE)
for key in "${required[@]}"; do
  value="$(env_value "$key")"
  [[ -n "$value" ]] || die "$key is empty"
  [[ "$value" != __*__ ]] || die "$key still contains a placeholder"
done

[[ "$(env_value CORS_ALLOWED_ORIGINS)" == "https://structify.cn" ]] || die "CORS_ALLOWED_ORIGINS must be exactly https://structify.cn"
[[ -z "$(env_value BOOTSTRAP_ADMIN_EMAIL)" ]] || die "BOOTSTRAP_ADMIN_EMAIL must be empty in production"
[[ -z "$(env_value TEACHER_EMAILS)" ]] || die "TEACHER_EMAILS must be empty in production"
[[ "$(env_value ALLOW_FIRST_USER_TEACHER)" =~ ^(false|0|no|off)$ ]] || die "ALLOW_FIRST_USER_TEACHER must be false"
[[ "$(env_value JWT_SECRET)" =~ ^.{64,}$ ]] || die "JWT_SECRET must be at least 64 characters"
[[ "$(env_value AUTH_COOKIE_SECURE)" != "false" ]] || die "AUTH_COOKIE_SECURE must not be false"
[[ "$(env_value AUTH_EXPOSE_DEV_CODE)" =~ ^(false|0|no|off)$ ]] || die "AUTH_EXPOSE_DEV_CODE must be false"
[[ "$(env_value AUTH_MAIL_ENABLED)" =~ ^(true|1|yes|on)$ ]] || die "AUTH_MAIL_ENABLED must be true"

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

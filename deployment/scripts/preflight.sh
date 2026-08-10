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

required=(MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET NODE_COMPAT_JWT_SECRET KNOWLEDGE_DIR_HOST RESOURCE_DIR_HOST PRESENTATION_DIR_HOST PDF_SOURCE_DIR_HOST NODE_IMAGE SPRING_IMAGE)
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
  if [[ "$EXECUTE" == "1" ]]; then
    host_caddy_config="$(env_value HOST_CADDY_CONFIG)"
    [[ -n "$host_caddy_config" ]] || die "HOST_CADDY_CONFIG is required in host Caddy mode"
    [[ "$host_caddy_config" == /* ]] || die "HOST_CADDY_CONFIG must be an absolute Linux path"
    [[ -r "$host_caddy_config" ]] || die "HOST_CADDY_CONFIG is not readable: $host_caddy_config"
    require_command caddy
    caddy validate --config "$host_caddy_config" --adapter caddyfile >/dev/null \
      || die "host Caddy configuration validation failed"
    log "host Caddy configuration validated"
  fi
else
  acme_email="$(env_value ACME_EMAIL)"
  [[ -n "$acme_email" && "$acme_email" != __*__ ]] || die "ACME_EMAIL is required when CADDY_MODE=container"
  log "container Caddy mode: Structify owns public 80/443"
  if [[ "$EXECUTE" == "1" ]]; then
    require_command ss
    for public_port in 80 443; do
      public_listeners="$(ss -H -ltn "sport = :$public_port" 2>/dev/null || true)"
      [[ -z "$public_listeners" ]] \
        || die "public TCP port $public_port is already bound; CADDY_MODE=container requires a dedicated host"
    done
    log "public TCP ports 80 and 443 are available for container Caddy"
  fi
fi

memory_mib() {
  local key="$1"
  local value="$2"
  [[ "$value" =~ ^([0-9]+)([mM])$ ]] \
    || die "$key must be a whole number of MiB with an m suffix"
  local amount="${BASH_REMATCH[1]}"
  (( 10#$amount >= 16 )) || die "$key must be at least 16 MiB"
  printf '%s\n' "$((10#$amount))"
}

whole_mib() {
  local key="$1"
  local value="$2"
  [[ "$value" =~ ^[0-9]+$ ]] || die "$key must be a whole number of MiB"
  printf '%s\n' "$((10#$value))"
}

configured_memory_mib() {
  local key="$1"
  local fallback="$2"
  local value
  value="$(env_value "$key")"
  value="${value:-$fallback}"
  memory_mib "$key" "$value"
}

if [[ "$EXECUTE" == "1" ]]; then
  memory_profile="$(env_value MEMORY_PROFILE)"
  memory_profile="${memory_profile:-low-memory}"
  case "$memory_profile" in
    low-memory)
      profile_minimum_mb=1024
      profile_reserve_mb=256
      profile_hard_limit_mb=1088
      ;;
    standard)
      profile_minimum_mb=1536
      profile_reserve_mb=384
      profile_hard_limit_mb=2048
      ;;
    *) die "MEMORY_PROFILE must be low-memory or standard" ;;
  esac

  minimum_available_memory_mb="$(env_value MIN_AVAILABLE_MEMORY_MB)"
  minimum_available_memory_mb="${minimum_available_memory_mb:-$profile_minimum_mb}"
  minimum_available_memory_mb="$(whole_mib MIN_AVAILABLE_MEMORY_MB "$minimum_available_memory_mb")"
  (( minimum_available_memory_mb >= profile_minimum_mb )) \
    || die "MIN_AVAILABLE_MEMORY_MB must be at least ${profile_minimum_mb} MiB for MEMORY_PROFILE=$memory_profile"

  memory_reserve_mb="$(env_value MEMORY_RESERVE_MB)"
  memory_reserve_mb="${memory_reserve_mb:-$profile_reserve_mb}"
  memory_reserve_mb="$(whole_mib MEMORY_RESERVE_MB "$memory_reserve_mb")"
  (( memory_reserve_mb >= 128 )) || die "MEMORY_RESERVE_MB must be at least 128 MiB"

  mysql_limit_mb="$(configured_memory_mib MYSQL_MEMORY_LIMIT 384m)"
  node_limit_mb="$(configured_memory_mib NODE_MEMORY_LIMIT 256m)"
  spring_limit_mb="$(configured_memory_mib SPRING_MEMORY_LIMIT 384m)"
  caddy_limit_mb="$(configured_memory_mib CADDY_MEMORY_LIMIT 64m)"
  mysql_reservation_mb="$(configured_memory_mib MYSQL_MEMORY_RESERVATION 256m)"
  node_reservation_mb="$(configured_memory_mib NODE_MEMORY_RESERVATION 160m)"
  spring_reservation_mb="$(configured_memory_mib SPRING_MEMORY_RESERVATION 288m)"
  caddy_reservation_mb="$(configured_memory_mib CADDY_MEMORY_RESERVATION 64m)"
  node_max_old_space_mb="$(env_value NODE_MAX_OLD_SPACE_MB)"
  node_max_old_space_mb="${node_max_old_space_mb:-160}"
  node_max_old_space_mb="$(whole_mib NODE_MAX_OLD_SPACE_MB "$node_max_old_space_mb")"
  (( node_max_old_space_mb < node_limit_mb )) \
    || die "NODE_MAX_OLD_SPACE_MB must be below NODE_MEMORY_LIMIT"

  for service in mysql node spring caddy; do
    limit_var="${service}_limit_mb"
    reservation_var="${service}_reservation_mb"
    (( ${!reservation_var} <= ${!limit_var} )) \
      || die "${service^^}_MEMORY_RESERVATION must not exceed ${service^^}_MEMORY_LIMIT"
  done

  total_hard_limit_mb=$((mysql_limit_mb + node_limit_mb + spring_limit_mb + caddy_limit_mb))
  total_reservation_mb=$((mysql_reservation_mb + node_reservation_mb + spring_reservation_mb + caddy_reservation_mb))
  (( total_hard_limit_mb <= profile_hard_limit_mb )) \
    || die "${memory_profile} service memory limits total ${total_hard_limit_mb} MiB exceeds hard cap ${profile_hard_limit_mb} MiB"
  evidence_budget_mb=$((total_reservation_mb + memory_reserve_mb))
  memory_budget_mb="$(env_value MEMORY_BUDGET_MB)"
  memory_budget_mb="${memory_budget_mb:-$evidence_budget_mb}"
  memory_budget_mb="$(whole_mib MEMORY_BUDGET_MB "$memory_budget_mb")"
  (( memory_budget_mb >= evidence_budget_mb )) \
    || die "MEMORY_BUDGET_MB ${memory_budget_mb} MiB is below declared reservations plus reserve ${evidence_budget_mb} MiB"
  hard_budget_mb=$((total_hard_limit_mb + memory_reserve_mb))
  effective_memory_budget_mb="$memory_budget_mb"
  (( effective_memory_budget_mb >= hard_budget_mb )) || effective_memory_budget_mb="$hard_budget_mb"

  available_memory_kib="$(awk '/^MemAvailable:/ { print $2; exit }' /proc/meminfo 2>/dev/null || true)"
  [[ "$available_memory_kib" =~ ^[0-9]+$ ]] \
    || die "cannot read MemAvailable from /proc/meminfo; execute deployment only on a Linux host"
  available_memory_mb=$((10#$available_memory_kib / 1024))
  (( available_memory_mb >= minimum_available_memory_mb )) \
    || die "available memory ${available_memory_mb} MiB is below configured floor ${minimum_available_memory_mb} MiB"
  (( available_memory_mb >= effective_memory_budget_mb )) \
    || die "configured memory budget ${memory_budget_mb} MiB (effective minimum ${effective_memory_budget_mb} MiB) exceeds available memory ${available_memory_mb} MiB"
  log "memory profile ${memory_profile}: service hard cap ${total_hard_limit_mb} MiB; reservations ${total_reservation_mb} MiB + reserve ${memory_reserve_mb} MiB"
  log "memory budget ${memory_budget_mb} MiB (effective ${effective_memory_budget_mb} MiB) meets available memory ${available_memory_mb} MiB"
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

for path_key in KNOWLEDGE_DIR_HOST RESOURCE_DIR_HOST PRESENTATION_DIR_HOST PDF_SOURCE_DIR_HOST; do
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

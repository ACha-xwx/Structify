#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

EXECUTE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    -h|--help) printf '%s\n' 'Usage: health-check.sh [--env-file FILE] [--execute]'; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done

commands=(
  "curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8791/healthz"
  "curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8792/actuator/health"
  "docker compose --env-file $ENV_FILE -f $COMPOSE_FILE ps"
)
if [[ "$EXECUTE" != "1" ]]; then
  log "dry-run health checks"
  for command in "${commands[@]}"; do printf '+ %s\n' "$command"; done
  exit 0
fi

require_command curl
require_command docker
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8791/healthz >/dev/null
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8792/actuator/health >/dev/null
compose ps
log "loopback health checks passed"

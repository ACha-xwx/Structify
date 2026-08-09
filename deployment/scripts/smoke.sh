#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-https://structify.cn}"
PRESENTATION_PATH=""
EXECUTE=0

usage() {
  cat <<'EOF'
Usage: smoke.sh [--domain URL] [--presentation-path /presentation/...]
                 [--execute]

Default mode is a local plan only. Execute mode performs read-only HTTPS
smoke checks; it does not log in, send model prompts, upload files, or mutate
DNS. SSE is verified from the proxy configuration and can be exercised only
with an explicit authenticated client flow.
EOF
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain) DOMAIN="$2"; shift 2 ;;
    --presentation-path) PRESENTATION_PATH="$2"; shift 2 ;;
    --execute) EXECUTE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'unknown option: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if [[ -n "$PRESENTATION_PATH" ]]; then
  [[ "$PRESENTATION_PATH" == /presentation/* ]] || {
    printf '%s\n' '--presentation-path must start with /presentation/' >&2
    exit 2
  }
  [[ "$PRESENTATION_PATH" != *\?* && "$PRESENTATION_PATH" != *\#* ]] || {
    printf '%s\n' '--presentation-path must not contain a query or fragment; do not pass signed tokens to smoke output' >&2
    exit 2
  }
fi

if [[ "$EXECUTE" != "1" ]]; then
  printf '%s\n' "dry-run smoke plan for $DOMAIN"
  printf '%s\n' "+ curl --fail --max-time 15 $DOMAIN/"
  printf '%s\n' "+ curl --fail --max-time 15 $DOMAIN/healthz"
  printf '%s\n' "+ curl --fail --max-time 15 -H 'Origin: https://structify.cn' -X OPTIONS $DOMAIN/api/v1/chapters"
  if [[ -n "$PRESENTATION_PATH" ]]; then
    printf '%s\n' "+ curl --max-time 15 $DOMAIN$PRESENTATION_PATH (expect 401 without JWT/signature)"
  fi
  exit 0
fi

command -v curl >/dev/null 2>&1 || { printf '%s\n' 'curl is required' >&2; exit 1; }
curl --fail --silent --show-error --max-time 15 "$DOMAIN/" >/dev/null
health="$(curl --fail --silent --show-error --max-time 15 "$DOMAIN/healthz")"
printf '%s\n' "$health" | grep -q '"ok":true'
cors_headers="$(curl --silent --show-error --max-time 15 -D - -o /dev/null -H 'Origin: https://structify.cn' -X OPTIONS "$DOMAIN/api/v1/chapters")"
printf '%s\n' "$cors_headers" | grep -qi '^access-control-allow-origin: https://structify.cn'
printf '%s\n' "$cors_headers" | grep -qi '^access-control-allow-credentials: true'
if [[ -n "$PRESENTATION_PATH" ]]; then
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$DOMAIN$PRESENTATION_PATH")"
  [[ "$status" == "401" || "$status" == "404" ]] || { printf 'unexpected unsigned presentation status: %s\n' "$status" >&2; exit 1; }
fi
printf '%s\n' 'HTTPS smoke checks passed; no authenticated or mutating flow was attempted.'

#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-https://structify.cn}"
ADMIN_DOMAIN="${ADMIN_DOMAIN:-https://admin.structify.cn}"
PUBLIC_ORIGIN="https://structify.cn"
ADMIN_ORIGIN="https://admin.structify.cn"
PRESENTATION_PATH=""
EXECUTE=0

usage() {
  cat <<'EOF'
Usage: smoke.sh [--domain URL] [--admin-domain URL]
                [--presentation-path /presentation/...]
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
    --admin-domain) ADMIN_DOMAIN="$2"; shift 2 ;;
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
  printf '%s\n' "+ curl --fail --max-time 15 --head $DOMAIN/"
  printf '%s\n' "+ curl --fail --max-time 15 $DOMAIN/healthz"
  printf '%s\n' "+ curl --max-time 15 --head $ADMIN_DOMAIN/ (expect 308 Location: /admin)"
  printf '%s\n' "+ curl --fail --max-time 15 $ADMIN_DOMAIN/admin and verify its hashed assets stay on the admin origin"
  printf '%s\n' "+ curl --max-time 15 --head $ADMIN_DOMAIN/user/chapters?admin-host-contract=1 (expect 308 Location: $PUBLIC_ORIGIN/user/chapters?admin-host-contract=1)"
  printf '%s\n' "+ curl --fail --max-time 15 $ADMIN_DOMAIN/healthz"
  printf '%s\n' "+ curl --fail --max-time 15 -H 'Origin: $PUBLIC_ORIGIN' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization,content-type,x-request-id' -X OPTIONS $DOMAIN/api/v1/chapters"
  printf '%s\n' "+ curl --fail --max-time 15 -H 'Origin: $ADMIN_ORIGIN' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization,content-type,x-request-id' -X OPTIONS $ADMIN_DOMAIN/api/v1/admin/capabilities"
  printf '%s\n' "+ curl --max-time 15 $ADMIN_DOMAIN/api/v1/admin/capabilities (expect 401 without credentials)"
  if [[ -n "$PRESENTATION_PATH" ]]; then
    printf '%s\n' "+ curl --max-time 15 $DOMAIN$PRESENTATION_PATH (expect 401 without JWT/signature)"
  fi
  exit 0
fi

command -v curl >/dev/null 2>&1 || { printf '%s\n' 'curl is required' >&2; exit 1; }

expect_redirect() {
  local url="$1"
  local expected_location="$2"
  local headers normalized_headers
  headers="$(curl --silent --show-error --max-time 15 --head "$url")"
  normalized_headers="${headers//$'\r'/}"
  printf '%s\n' "$normalized_headers" | grep -Eq '^HTTP/[0-9.]+ 308([[:space:]]|$)' \
    || { printf 'expected 308 from %s\n' "$url" >&2; return 1; }
  printf '%s\n' "$normalized_headers" | grep -Fxi "location: $expected_location" >/dev/null \
    || { printf 'unexpected redirect location from %s; expected %s\n' "$url" "$expected_location" >&2; return 1; }
}

curl --fail --silent --show-error --max-time 15 --head "$DOMAIN/" >/dev/null
health="$(curl --fail --silent --show-error --max-time 15 "$DOMAIN/healthz")"
printf '%s\n' "$health" | grep -q '"ok":true'
expect_redirect "$ADMIN_DOMAIN/" "/admin"

admin_html="$(curl --fail --silent --show-error --max-time 15 "$ADMIN_DOMAIN/admin")"
admin_asset_paths="$(printf '%s' "$admin_html" | grep -Eo '/assets/[^"'"'"' ]+\.(js|css)' | sort -u)"
[[ -n "$admin_asset_paths" ]] || { printf '%s\n' 'admin shell does not reference hashed assets' >&2; exit 1; }
while IFS= read -r asset_path; do
  [[ -n "$asset_path" ]] || continue
  asset_headers="$(curl --fail --silent --show-error --max-time 15 --head "$ADMIN_DOMAIN$asset_path")"
  normalized_asset_headers="${asset_headers//$'\r'/}"
  printf '%s\n' "$normalized_asset_headers" | grep -Eq '^HTTP/[0-9.]+ 200([[:space:]]|$)' \
    || { printf 'admin asset did not return 200: %s\n' "$asset_path" >&2; exit 1; }
  if printf '%s\n' "$normalized_asset_headers" | grep -Eqi '^location:'; then
    printf 'admin asset unexpectedly redirected: %s\n' "$asset_path" >&2
    exit 1
  fi
done <<< "$admin_asset_paths"
expect_redirect "$ADMIN_DOMAIN/user/chapters?admin-host-contract=1" "$PUBLIC_ORIGIN/user/chapters?admin-host-contract=1"
admin_health="$(curl --fail --silent --show-error --max-time 15 "$ADMIN_DOMAIN/healthz")"
printf '%s\n' "$admin_health" | grep -q '"ok":true'

check_cors() {
  local domain="$1"
  local origin="$2"
  local path="$3"
  local cors_headers normalized_headers
  cors_headers="$(curl --silent --show-error --max-time 15 -D - -o /dev/null \
    -H "Origin: $origin" \
    -H 'Access-Control-Request-Method: GET' \
    -H 'Access-Control-Request-Headers: authorization,content-type,x-request-id' \
    -X OPTIONS "$domain$path")"
  normalized_headers="${cors_headers//$'\r'/}"
  [[ "$normalized_headers" == HTTP/*" 204 "* ]]
  [[ "$normalized_headers" == *"access-control-allow-origin: $origin"* ]]
  [[ "$normalized_headers" == *"access-control-allow-credentials: true"* ]]
}

check_cors "$DOMAIN" "$PUBLIC_ORIGIN" "/api/v1/chapters"
check_cors "$ADMIN_DOMAIN" "$ADMIN_ORIGIN" "/api/v1/admin/capabilities"
admin_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$ADMIN_DOMAIN/api/v1/admin/capabilities")"
[[ "$admin_status" == "401" ]] || { printf 'unexpected unsigned admin capability status: %s\n' "$admin_status" >&2; exit 1; }
if [[ -n "$PRESENTATION_PATH" ]]; then
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$DOMAIN$PRESENTATION_PATH")"
  [[ "$status" == "401" || "$status" == "404" ]] || { printf 'unexpected unsigned presentation status: %s\n' "$status" >&2; exit 1; }
  admin_presentation_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$ADMIN_DOMAIN$PRESENTATION_PATH")"
  [[ "$admin_presentation_status" == "401" || "$admin_presentation_status" == "404" ]] \
    || { printf 'unexpected unsigned admin presentation status: %s\n' "$admin_presentation_status" >&2; exit 1; }
fi
printf '%s\n' 'HTTPS smoke checks passed; no authenticated or mutating flow was attempted.'

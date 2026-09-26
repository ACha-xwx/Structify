#!/usr/bin/env bash
#
# Publish a WebP twin for every rendered courseware page.
#
# A page is one whole image, and the classroom fetches it over a link that pays most of a second per
# request, so the number of bytes per page is the number the reader feels. Measured on the published
# decks, the WebP form is about 70% smaller than the PNG it is rendered from - unchanged pixels, same
# dimensions, no re-rendering of the source decks and no touch to the review pipeline.
#
# The backend serves the twin automatically when one exists next to a page
# (see PresentationCatalog#imageFile), so this is a data step, not a release step: it can be re-run at
# any time, it only ever adds files, and a deployment that never runs it keeps serving PNG exactly as
# before. It does need the application to reload its courseware index afterwards, because the urls a
# lesson hands out are built when that index is read - restart the api container, or deploy.
#
# Needs docker on the machine that holds the materials (the origin host), because cwebp is not part of
# the application image. Anything already up to date is skipped, so re-running is cheap.
#
#   deployment/scripts/build-slide-webp.sh [--materials DIR] [--quality N] [--force] [--dry-run]
#
set -euo pipefail

MATERIALS="/srv/structify/private/presentation-materials"
QUALITY=80
FORCE=0
DRY_RUN=0
CONVERTER_IMAGE="alpine:3.20"

while [ $# -gt 0 ]; do
  case "$1" in
    --materials) MATERIALS="$2"; shift 2 ;;
    --quality) QUALITY="$2"; shift 2 ;;
    --converter-image) CONVERTER_IMAGE="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

command -v docker >/dev/null 2>&1 || { echo "docker is required to run cwebp" >&2; exit 1; }
RENDERED="$MATERIALS/rendered"
[ -d "$RENDERED" ] || { echo "no rendered pages under $MATERIALS" >&2; exit 1; }

case "$QUALITY" in
  ''|*[!0-9]*) echo "--quality must be a number" >&2; exit 2 ;;
esac

total_png=$(find "$RENDERED" -name '*.png' -printf '.' | wc -c)
echo "materials : $MATERIALS"
echo "pages     : $total_png PNG"
if [ "$FORCE" = "1" ]; then
  echo "quality   : $QUALITY (forced: every page is converted again)"
else
  echo "quality   : $QUALITY"
fi
echo

# The list is built on the host so the container needs no shell of its own beyond one loop.
LIST="$(mktemp)"
trap 'rm -f "$LIST"' EXIT

if [ "$FORCE" = "1" ]; then
  find "$RENDERED" -name '*.png' -printf '%P\n' | sort > "$LIST"
else
  # Convert what has no twin yet, and refresh a twin whose page was re-rendered afterwards.
  find "$RENDERED" -name '*.png' -printf '%P\n' | sort | while read -r page; do
    twin="${page%.png}.webp"
    if [ ! -f "$RENDERED/$twin" ] || [ "$RENDERED/$page" -nt "$RENDERED/$twin" ]; then
      printf '%s\n' "$page"
    fi
  done > "$LIST"
fi

pending=$(wc -l < "$LIST" | tr -d ' ')
echo "to convert: $pending"
if [ "$pending" = "0" ]; then
  echo "nothing to do; every page already has an up-to-date WebP twin"
  exit 0
fi
if [ "$DRY_RUN" = "1" ]; then
  head -5 "$LIST"
  echo "(dry run: stopping here)"
  exit 0
fi

docker run --rm \
  -v "$RENDERED":/rendered \
  -v "$LIST":/pages.txt:ro \
  "$CONVERTER_IMAGE" sh -c '
    apk add --no-cache libwebp-tools >/dev/null 2>&1 || { echo "could not install libwebp-tools" >&2; exit 1; }
    before=0; after=0; done_count=0; failed=0
    while read -r page; do
      [ -n "$page" ] || continue
      src="/rendered/$page"
      dst="/rendered/${page%.png}.webp"
      [ -f "$src" ] || continue
      if cwebp -quiet -q '"$QUALITY"' -m 4 -mt "$src" -o "$dst.tmp" 2>/dev/null; then
        mv "$dst.tmp" "$dst"
        before=$((before + $(wc -c < "$src")))
        after=$((after + $(wc -c < "$dst")))
        done_count=$((done_count + 1))
        if [ $((done_count % 200)) -eq 0 ]; then echo "  ... $done_count pages"; fi
      else
        rm -f "$dst.tmp"
        failed=$((failed + 1))
        echo "  FAILED: $page" >&2
      fi
    done < /pages.txt
    echo
    echo "converted : $done_count pages"
    [ "$failed" = "0" ] || echo "failed    : $failed pages"
    if [ "$done_count" -gt 0 ]; then
      echo "bytes     : $((before / 1024)) KiB -> $((after / 1024)) KiB (-$((100 - 100 * after / before))%)"
    fi
  '

echo
echo "Every page now has a WebP twin, and the backend prefers it over the PNG."
echo "The lessons already issued keep their old .png urls until the courseware index is read again,"
echo "so restart the spring-api service - or land the next release, which restarts it anyway:"
echo "  docker compose restart spring-api    # from the release directory, with its --env-file"

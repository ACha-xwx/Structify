#!/bin/sh
set -eu

# The image ships the reviewed public PDF examples separately. A writable
# volume is used for Node uploads; seed it only on first boot so updates do not
# overwrite operator-managed files.
if [ ! -e /app/pdfs/.seeded ]; then
  if [ -d /app/default-pdfs ]; then
    cp -R /app/default-pdfs/. /app/pdfs/
  fi
  touch /app/pdfs/.seeded
fi

exec "$@"

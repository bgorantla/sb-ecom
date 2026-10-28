#!/usr/bin/env bash
# Optional: copy your local PostgreSQL data and product images to the server,
# so the live store has the same categories, products and users as your laptop.
# This REPLACES the server's tables with your local ones, including user
# passwords, so run change-password.sh afterwards.
#
# Usage, from the project root:
#   bash deploy/copy-local-data.sh <ssh-host>
# Local database defaults to postgres@localhost/ecommerce; override with e.g.
#   LOCAL_DB_URL=postgresql://postgres:secret@localhost:5432/ecommerce bash deploy/copy-local-data.sh sb-ecom
set -euo pipefail

HOST="${1:?usage: bash deploy/copy-local-data.sh <ssh-host>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL_DB_URL="${LOCAL_DB_URL:-postgresql://postgres@localhost:5432/ecommerce}"
DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT

echo "==> Dumping the local database"
pg_dump --clean --if-exists --no-owner --no-privileges "$LOCAL_DB_URL" > "$DUMP"

echo "==> Uploading the dump and images"
scp -q "$DUMP" "$HOST:/tmp/ecom.sql"
rsync -az "$ROOT/sb-ecom/images/" "$HOST:/tmp/ecom-images/"

echo "==> Restoring on the server"
ssh "$HOST" 'set -e
  sudo systemctl stop sb-ecom || true
  export PGPASSWORD="$(sudo sed -n "s/^DB_PASSWORD=//p" /etc/sb-ecom.env)"
  # A newer local Postgres can print a few harmless "unrecognized parameter" errors here
  psql -q -h localhost -U ecom -d ecommerce -f /tmp/ecom.sql >/dev/null
  sudo cp -r /tmp/ecom-images/. /opt/sb-ecom/images/
  sudo chown -R sbecom:sbecom /opt/sb-ecom/images
  rm -rf /tmp/ecom.sql /tmp/ecom-images
  sudo systemctl start sb-ecom'

echo "Done. The backend is restarting with your local data."

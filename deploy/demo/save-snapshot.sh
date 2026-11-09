#!/usr/bin/env bash
# Saves the store as it is right now (database + product photos) as the demo's
# clean state. The nightly reset (reset.sh) puts everything back to this.
#
# Run it on the server after you change the catalog on purpose, or the next
# nightly reset will undo your changes:
#   ssh sb-ecom 'sudo /opt/sb-ecom/demo/save-snapshot.sh'
set -euo pipefail

DEMO=/opt/sb-ecom/demo
PGPASSWORD="$(sed -n 's/^DB_PASSWORD=//p' /etc/sb-ecom.env)"
export PGPASSWORD

# Leave out database extensions (change-password.sh may have added pgcrypto): only the
# postgres superuser can recreate those, so restoring them as the app's user would fail.
pg_dump -h localhost -U ecom --clean --if-exists --no-owner --no-privileges ecommerce \
  | grep -v -E '^(DROP|CREATE|COMMENT ON) EXTENSION' > "$DEMO/snapshot.sql.tmp"
mv "$DEMO/snapshot.sql.tmp" "$DEMO/snapshot.sql"
rsync -a --delete /opt/sb-ecom/images/ "$DEMO/images/"
chmod 600 "$DEMO/snapshot.sql"   # it contains password hashes

echo "Snapshot saved: $(grep -c '^COPY ' "$DEMO/snapshot.sql") tables, $(ls "$DEMO/images" | wc -l) photos."

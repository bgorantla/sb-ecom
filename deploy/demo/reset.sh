#!/usr/bin/env bash
# Puts the demo store back to the snapshot saved by save-snapshot.sh: throwaway
# shopper accounts, their carts and orders disappear, and the catalog and photos
# return to how they were. The site is down for about half a minute.
#
# Runs every night through sb-ecom-demo-reset.timer. To run it by hand:
#   ssh sb-ecom 'sudo /opt/sb-ecom/demo/reset.sh'
set -euo pipefail

DEMO=/opt/sb-ecom/demo
[ -f "$DEMO/snapshot.sql" ] || { echo "No snapshot yet: run save-snapshot.sh first" >&2; exit 1; }

PGPASSWORD="$(sed -n 's/^DB_PASSWORD=//p' /etc/sb-ecom.env)"
export PGPASSWORD

systemctl stop sb-ecom
trap 'systemctl start sb-ecom' EXIT   # start the app again even if something below fails

# The snapshot drops each table and recreates it with the saved rows. It runs as one
# transaction, so if anything fails the database is left exactly as it was.
psql -q -h localhost -U ecom -d ecommerce -v ON_ERROR_STOP=1 --single-transaction -f "$DEMO/snapshot.sql" > /dev/null
rsync -a --delete "$DEMO/images/" /opt/sb-ecom/images/
chown -R sbecom:sbecom /opt/sb-ecom/images

echo "Demo store reset to the saved snapshot."

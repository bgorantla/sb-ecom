#!/usr/bin/env bash
# Turns the live site into a public demo. Run it once, after deploying the code
# that adds demo mode. It:
#   1. checks your admin password (and refuses if it's still the public default)
#   2. installs the snapshot script and the nightly reset on the server
#   3. turns on demo mode (read-only demo admin, test-mode banner) and stops
#      creating the user1/seller1 accounts, whose passwords are public
#   4. optionally empties the store first (your admin account is kept)
#   5. adds the 35-product catalog from deploy/demo/catalog.json, with photos
#   6. saves that as the state the store returns to every night
#
# Usage, from the project root:
#   bash deploy/demo-setup.sh sb-ecom
set -euo pipefail

HOST="${1:?usage: bash deploy/demo-setup.sh <ssh-host>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

read -rsp "Password of your 'admin' account on the live site: " ADMIN_PW; echo
[ -n "$ADMIN_PW" ] || { echo "No password given." >&2; exit 1; }
read -rp "Start from an empty store? This deletes all products, orders and other users [y/N] " ANSWER
FRESH=n; [[ "$ANSWER" =~ ^[Yy]$ ]] && FRESH=y

echo "==> Copying the demo files to the server"
rsync -az --delete "$ROOT/deploy/demo/" "$HOST:/tmp/sb-ecom-demo/"

# The password goes over SSH on standard input, so it never appears in a command line
printf '%s\n' "$ADMIN_PW" | ssh "$HOST" "bash /tmp/sb-ecom-demo/install.sh $FRESH"

#!/usr/bin/env bash
# Server half of deploy/demo-setup.sh (which copies this folder to the server and runs it).
# Reads the admin password from standard input. Argument: "y" to empty the store first.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
FRESH="${1:-n}"
DEMO=/opt/sb-ecom/demo
read -r ADMIN_PASSWORD
export ADMIN_PASSWORD

wait_for_api() {
  for _ in $(seq 1 60); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/api/public/products)" = 200 ] && return 0
    sleep 3
  done
  echo "The backend didn't come back up. See: sudo journalctl -u sb-ecom -n 100 --no-pager" >&2
  exit 1
}

echo "==> Checking your admin login"
if ADMIN_PASSWORD=adminPass python3 "$HERE/seed_catalog.py" --check-login >/dev/null 2>&1; then
  echo "Your admin account still has the default password (adminPass), which is public on GitHub." >&2
  echo "Set your own first:  bash deploy/change-password.sh <ssh-host> admin" >&2
  exit 1
fi
python3 "$HERE/seed_catalog.py" --check-login

echo "==> Installing the snapshot and nightly reset scripts"
sudo install -d -m 755 "$DEMO" "$DEMO/images"
sudo install -m 755 "$HERE/reset.sh" "$HERE/save-snapshot.sh" "$DEMO/"
sudo install -m 644 "$HERE/seed_catalog.py" "$HERE/catalog.json" "$HERE/clear-store.sql" "$DEMO/"
sudo install -m 644 "$HERE/sb-ecom-demo-reset.service" "$HERE/sb-ecom-demo-reset.timer" /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sb-ecom-demo-reset.timer >/dev/null 2>&1

echo "==> Turning on demo mode in /etc/sb-ecom.env"
set_env() {  # replace the line if it's there, otherwise add it
  if sudo grep -q "^$1=" /etc/sb-ecom.env; then
    sudo sed -i "s/^$1=.*/$1=$2/" /etc/sb-ecom.env
  else
    echo "$1=$2" | sudo tee -a /etc/sb-ecom.env >/dev/null
  fi
}
set_env DEMO_MODE true
set_env SEED_SAMPLE_USERS false
# Only used if the app ever has to create "admin" from scratch. Without it the app would
# fall back to the public default password, so set a random one if there isn't one yet.
sudo grep -q '^ADMIN_PASSWORD=' /etc/sb-ecom.env || set_env ADMIN_PASSWORD "$(openssl rand -hex 12)"

if [ "$FRESH" = y ]; then
  echo "==> Emptying the store (your admin account is kept)"
  sudo systemctl stop sb-ecom
  PGPASSWORD="$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/sb-ecom.env)" \
    psql -q -h localhost -U ecom -d ecommerce -v ON_ERROR_STOP=1 -f "$HERE/clear-store.sql"
  sudo find /opt/sb-ecom/images -type f -delete
  sudo systemctl start sb-ecom
else
  sudo systemctl restart sb-ecom   # picks up DEMO_MODE and creates the demo admin
fi
wait_for_api

echo "==> Adding the catalog (downloads 35 product photos)"
python3 "$HERE/seed_catalog.py" --api http://127.0.0.1 --catalog "$HERE/catalog.json"

echo "==> Saving this as the state the store resets to every night"
sudo "$DEMO/save-snapshot.sh"

rm -rf "$HERE"
echo
echo "Demo is on. Next reset: $(systemctl show sb-ecom-demo-reset.timer -p NextElapseUSecRealtime --value)"

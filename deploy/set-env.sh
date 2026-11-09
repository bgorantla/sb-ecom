#!/usr/bin/env bash
# Sets one setting in /etc/sb-ecom.env on the server, then restarts the backend.
# The value is typed in hidden and sent over SSH as input, never on a command line.
#
# Usage, from the project root:
#   bash deploy/set-env.sh <ssh-host> <NAME>
# e.g.
#   bash deploy/set-env.sh sb-ecom STRIPE_WEBHOOK_SECRET
#   bash deploy/set-env.sh sb-ecom STRIPE_SECRET_KEY
set -euo pipefail

HOST="${1:?usage: bash deploy/set-env.sh <ssh-host> <NAME>}"
NAME="${2:?usage: bash deploy/set-env.sh <ssh-host> <NAME>}"
[[ "$NAME" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "Setting names look like STRIPE_WEBHOOK_SECRET" >&2; exit 1; }

read -rsp "Value for $NAME (typing is hidden): " VALUE; echo
# Stripe keys and secrets only use these characters; anything else is probably a paste mistake
[[ "$VALUE" =~ ^[A-Za-z0-9_.:/+=@-]+$ ]] || { echo "That value has unexpected characters (spaces or quotes?)." >&2; exit 1; }

# Runs on the server. It reads the value from its input (the printf below).
REMOTE='set -euo pipefail
read -r VALUE
if sudo grep -q "^$NAME=" /etc/sb-ecom.env; then
  sudo sed -i "s|^$NAME=.*|$NAME=$VALUE|" /etc/sb-ecom.env
else
  echo "$NAME=$VALUE" | sudo tee -a /etc/sb-ecom.env >/dev/null
fi
sudo systemctl restart sb-ecom
for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/api/public/products)" = 200 ]; then
    echo "$NAME set; the backend is back up."
    exit 0
  fi
  sleep 3
done
echo "$NAME set, but the backend did not come back. See: sudo journalctl -u sb-ecom -n 100 --no-pager" >&2
exit 1'

# printf %q quotes the script so it survives the trip through ssh unchanged
printf '%s\n' "$VALUE" | ssh "$HOST" "NAME=$NAME bash -c $(printf %q "$REMOTE")"

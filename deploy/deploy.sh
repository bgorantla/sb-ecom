#!/usr/bin/env bash
# Build the backend and frontend on your laptop, upload them, and restart the app.
# Run it for the first deploy and every redeploy.
#
# Usage, from the project root:
#   bash deploy/deploy.sh <ssh-host>        e.g. bash deploy/deploy.sh sb-ecom
# <ssh-host> is the Host entry in ~/.ssh/config, or ubuntu@<elastic-ip>.
set -euo pipefail

HOST="${1:?usage: bash deploy/deploy.sh <ssh-host>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

ENV_FILE="$ROOT/ecom-frontend/.env.production"
if [ ! -f "$ENV_FILE" ] || grep -q REPLACE_ME "$ENV_FILE"; then
  echo "Set up ecom-frontend/.env.production first:" >&2
  echo "  cp deploy/frontend.env.production ecom-frontend/.env.production" >&2
  echo "  then fill in your CloudFront address and Stripe publishable key." >&2
  exit 1
fi
if grep -qE '(sk|rk)_(test|live)_' "$ENV_FILE"; then
  echo "ecom-frontend/.env.production contains a Stripe SECRET key (sk_...), and that file ends up in public JavaScript." >&2
  echo "Put the publishable key (pk_...) there. The secret key belongs in /etc/sb-ecom.env on the server." >&2
  exit 1
fi
if ! grep -qE '^VITE_BACK_END_URL=$' "$ENV_FILE" || ! grep -qE '^VITE_FRONTEND_URL=https://' "$ENV_FILE"; then
  echo "ecom-frontend/.env.production needs exactly these two lines:" >&2
  echo "  VITE_BACK_END_URL=                                   (nothing after the =)" >&2
  echo "  VITE_FRONTEND_URL=https://<your-id>.cloudfront.net   (with https://)" >&2
  exit 1
fi

echo "==> Building the backend jar"
(cd "$ROOT/sb-ecom" && bash ./mvnw -q clean package -DskipTests)
JARS=("$ROOT"/sb-ecom/target/sb-ecom-*.jar)
JAR="${JARS[0]}"

echo "==> Building the frontend (settings come from ecom-frontend/.env.production)"
cd "$ROOT/ecom-frontend"
[ -d node_modules ] || npm ci
# Variables already in your shell override .env files in Vite, so drop any
# left over from local testing.
env -u VITE_BACK_END_URL -u VITE_FRONTEND_URL -u VITE_STRIPE_PUBLISHABLE_KEY npm run build
cd "$ROOT"

echo "==> Uploading to $HOST"
scp -q "$JAR" "$HOST:/tmp/app.jar"
rsync -az --delete ecom-frontend/dist/ "$HOST:/var/www/sb-ecom/"

echo "==> Restarting the backend"
ssh "$HOST" 'sudo install -o sbecom -g sbecom -m 644 /tmp/app.jar /opt/sb-ecom/app.jar && rm -f /tmp/app.jar && sudo systemctl restart sb-ecom'

echo "==> Waiting for the API (can take a minute on a small instance)"
ssh "$HOST" 'for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/api/public/products)" = 200 ]; then
    echo "Deployed. The API is up."; exit 0
  fi
  sleep 3
done
echo "The API did not come up. See the logs: sudo journalctl -u sb-ecom -n 100 --no-pager" >&2
exit 1'

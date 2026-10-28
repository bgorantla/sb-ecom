#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 24.04 EC2 instance (t4g.micro / t3.micro).
# Installs Java 21, PostgreSQL and Nginx, creates the database and an app user,
# generates secrets, and installs the systemd service and Nginx site.
# Safe to re-run.
#
# Usage, on the server:
#   bash ~/deploy/server-setup.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ME="$(id -un)"

echo "==> Adding 2 GB of swap (the instance only has 1 GB of RAM)"
if ! sudo swapon --show=NAME --noheadings | grep -qx /swapfile; then
  [ -f /swapfile ] || sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-sb-ecom.conf >/dev/null
sudo sysctl -q vm.swappiness=10 || true

echo "==> Installing Java 21, PostgreSQL and Nginx"
sudo apt-get update -q
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-21-jre-headless postgresql nginx rsync

echo "==> Creating the app user and folders"
id sbecom >/dev/null 2>&1 || sudo useradd --system --home-dir /opt/sb-ecom --shell /usr/sbin/nologin sbecom
sudo install -d -o sbecom -g sbecom /opt/sb-ecom /opt/sb-ecom/images
sudo install -d -o "$ME" -g "$ME" /var/www/sb-ecom

echo "==> Writing /etc/sb-ecom.env"
if sudo test -f /etc/sb-ecom.env; then
  echo "    already exists, leaving it alone"
else
  DB_PASSWORD="$(openssl rand -hex 24)"
  JWT_SECRET="$(openssl rand -base64 32)"
  sudo install -m 640 -o root -g sbecom /dev/null /etc/sb-ecom.env
  sudo tee /etc/sb-ecom.env >/dev/null <<EOF
# Environment for the sb-ecom service. After editing: sudo systemctl restart sb-ecom
DB_URL=jdbc:postgresql://localhost:5432/ecommerce
DB_USERNAME=ecom
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
STRIPE_SECRET_KEY=sk_test_REPLACE_ME
FRONTEND_URL=https://REPLACE_ME.cloudfront.net
IMAGE_BASE_URL=/images
EOF
fi

echo "==> Creating the PostgreSQL user and database"
DB_PASSWORD="$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/sb-ecom.env)"
cd /tmp   # the postgres user can't read our home folder
if [ "$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='ecom'")" != 1 ]; then
  sudo -u postgres psql -q -v ON_ERROR_STOP=1 -v pw="$DB_PASSWORD" <<'SQL'
CREATE ROLE ecom LOGIN PASSWORD :'pw';
SQL
fi
if [ "$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='ecommerce'")" != 1 ]; then
  sudo -u postgres createdb -O ecom ecommerce
fi

echo "==> Installing the systemd service"
sudo install -m 644 "$HERE/sb-ecom.service" /etc/systemd/system/sb-ecom.service
sudo systemctl daemon-reload
sudo systemctl enable sb-ecom >/dev/null 2>&1

echo "==> Installing the Nginx site"
sudo install -m 644 "$HERE/nginx-sb-ecom.conf" /etc/nginx/sites-available/sb-ecom
sudo ln -sf /etc/nginx/sites-available/sb-ecom /etc/nginx/sites-enabled/sb-ecom
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t -q
sudo systemctl reload nginx

cat <<'MSG'

Server is ready. Next:
  1. sudo nano /etc/sb-ecom.env
       - STRIPE_SECRET_KEY = your sk_test_... key
       - FRONTEND_URL      = your CloudFront address (once you've created it)
  2. On your laptop, from the project folder: bash deploy/deploy.sh sb-ecom
MSG

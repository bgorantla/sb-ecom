#!/usr/bin/env bash
# Set a new password for a user, directly in the server's database.
# Use it for the seeded accounts (admin, seller1, user1): their default
# passwords are in WebSecurityConfig.java, which is public on GitHub.
# The seeder only creates missing users, so the new password survives restarts.
#
# Usage, from the project root:
#   bash deploy/change-password.sh <ssh-host> <username>      e.g. bash deploy/change-password.sh sb-ecom admin
set -euo pipefail

HOST="${1:?usage: bash deploy/change-password.sh <ssh-host> <username>}"
USERNAME="${2:?usage: bash deploy/change-password.sh <ssh-host> <username>}"
[[ "$USERNAME" =~ ^[A-Za-z0-9_.@-]+$ ]] || { echo "Unexpected characters in username" >&2; exit 1; }

read -rsp "New password for $USERNAME: " PW; echo
read -rsp "Repeat it: " PW2; echo
[ "$PW" = "$PW2" ] || { echo "Passwords don't match" >&2; exit 1; }
[ "${#PW}" -ge 6 ] || { echo "Use at least 6 characters" >&2; exit 1; }

# Sent base64-encoded so quotes or spaces in the password can't break the command
PW_B64="$(printf '%s' "$PW" | base64 | tr -d '\n')"

ssh "$HOST" "cd /tmp && sudo -u postgres psql -d ecommerce -v ON_ERROR_STOP=1 -v u='$USERNAME' -v pw_b64='$PW_B64'" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
UPDATE users
   SET password = crypt(convert_from(decode(:'pw_b64', 'base64'), 'UTF8'), gen_salt('bf', 10))
 WHERE username = :'u';
SQL
echo "If it printed UPDATE 1, the password is changed. UPDATE 0 means no user by that name."

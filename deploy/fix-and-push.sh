#!/usr/bin/env bash
# One-time cleanup after the first deploy. It:
#   1. writes ecom-frontend/.env.production correctly (publishable key, CloudFront URL)
#   2. puts your NEW Stripe secret key and the CloudFront URL on the server
#   3. redeploys, so the live site no longer contains the old secret key
#   4. deletes two stray files, then commits and pushes everything to GitHub
#      (after showing you the list and asking first)
#
# Before running:
#   - Roll your Stripe secret key: Stripe dashboard -> Developers -> API keys -> "..." next to the secret key -> Roll key
#   - Close vim (or any editor) if it still has ecom-frontend/.env.production open
#
# Usage, from the project root:
#   bash deploy/fix-and-push.sh sb-ecom
set -euo pipefail

HOST="${1:?usage: bash deploy/fix-and-push.sh <ssh-host>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ENV_FILE=ecom-frontend/.env.production
DEFAULT_CF=https://d1loonx3il11ot.cloudfront.net
die() { echo "Stopped: $*" >&2; exit 1; }

if ls ecom-frontend/.*.swp >/dev/null 2>&1; then
  echo "vim seems to have a file in ecom-frontend/ open (found a .swp file)."
  read -rp "Close vim first. Continue anyway? [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || exit 1
fi

# The secret key that was in .env.production by mistake (if it's still there)
LEAKED_KEY="$(sed -n 's/^VITE_STRIPE_PUBLISHABLE_KEY=\(sk_.*\)$/\1/p' "$ENV_FILE" 2>/dev/null || true)"

echo "==> Stripe keys and site address"
read -rp "CloudFront address [$DEFAULT_CF]: " CF
CF="${CF:-$DEFAULT_CF}"
CF="${CF%/}"
[[ "$CF" =~ ^https://[a-z0-9]+\.cloudfront\.net$ ]] || die "expected something like $DEFAULT_CF"
read -rp "Stripe PUBLISHABLE key (pk_test_...): " PK
[[ "$PK" =~ ^pk_(test|live)_[A-Za-z0-9]+$ ]] || die "that isn't a publishable key; it should start with pk_test_"
read -rsp "NEW Stripe SECRET key from after rolling (sk_test_..., typing is hidden): " SK; echo
[[ "$SK" =~ ^sk_(test|live)_[A-Za-z0-9]+$ ]] || die "that isn't a secret key; it should start with sk_test_"
[ "$SK" != "$LEAKED_KEY" ] || die "that's the old key that was exposed. Roll it in Stripe and paste the new one."

echo "==> Writing $ENV_FILE"
cat > "$ENV_FILE" <<EOF
# Read by \`npm run build\` (and so by deploy/deploy.sh). \`npm run dev\` ignores this file.
# Never put the Stripe secret key (sk_...) here: everything in this file ends up in public JavaScript.
VITE_BACK_END_URL=
VITE_FRONTEND_URL=$CF
VITE_STRIPE_PUBLISHABLE_KEY=$PK
EOF

echo "==> Updating the secret key and site address on the server"
printf '%s\n%s\n' "$SK" "$CF" | ssh "$HOST" 'read -r k; read -r u
  sudo sed -i -e "s#^STRIPE_SECRET_KEY=.*#STRIPE_SECRET_KEY=$k#" -e "s#^FRONTEND_URL=.*#FRONTEND_URL=$u#" /etc/sb-ecom.env
  sudo grep -q "^STRIPE_SECRET_KEY=$k\$" /etc/sb-ecom.env'
unset SK

echo "==> Redeploying"
bash deploy/deploy.sh "$HOST" </dev/null

echo "==> Removing stray files"
if [ -f config ] && [ ! -s config ]; then rm config && echo "    removed the empty ./config"; fi
for f in sb-ecom/hs_err_pid*.log; do [ -e "$f" ] && rm "$f" && echo "    removed $f (DBeaver crash log)"; done

echo "==> Preparing the git commit"
if ! git check-ignore -q "$ENV_FILE"; then printf '\n.env.production\n' >> ecom-frontend/.gitignore; fi
git check-ignore -q "$ENV_FILE" || die "$ENV_FILE is not ignored by git"
git add -A
if git diff --cached | grep -qE '(sk|rk)_(test|live)_[A-Za-z0-9]{10,}'; then
  git reset -q
  die "a Stripe secret key is in the files about to be committed. Nothing was committed."
fi
if git diff --cached --quiet; then echo "Nothing to commit."; exit 0; fi
echo "These changes will be committed and pushed to GitHub:"
git status --short
read -rp "Commit and push? [y/N] " ans
if ! [[ "$ans" =~ ^[Yy]$ ]]; then git reset -q; echo "Nothing committed. The site is fixed and redeployed."; exit 0; fi
git commit -q -F - <<'MSG'
Add AWS deployment scripts; fix Vite and Tailwind setup; read secrets from env vars

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_012ipnmwCtt323tMqa4N4VcA
MSG
git push
echo "All done: new Stripe key in place, site redeployed, changes pushed."

#!/usr/bin/env bash
# One-time setup for automatic deploys from GitHub Actions (the "deploy" job in
# .github/workflows/ci.yml). Run it on your laptop, from the project root:
#
#   bash deploy/setup-github-deploy.sh sb-ecom
#
# It
#   1. makes an SSH key only for GitHub and adds it to the server,
#   2. looks up everything the workflow needs: the server's address and host key, and the AWS
#      account, region and security group (asked from the server itself), plus the site address
#      and Stripe publishable key from ecom-frontend/.env.production,
#   3. saves those in your GitHub repository's settings with the gh CLI, if you have it,
#      or prints them for you to enter on github.com,
#   4. prints the two AWS policies to paste in the IAM console.
# Running it again is safe: it reuses the key and overwrites the settings with fresh values.
set -euo pipefail

HOST="${1:?usage: bash deploy/setup-github-deploy.sh <ssh-host>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEY="$HOME/.ssh/sb-ecom-github-deploy"
ROLE_NAME="github-actions-sb-ecom-deploy"

# ---- 1. An SSH key just for GitHub (so it can be removed without touching yours) ----
if [ ! -f "$KEY" ]; then
  mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh"
  ssh-keygen -q -t ed25519 -N "" -C "github-actions-deploy" -f "$KEY"
  echo "Made an SSH key for GitHub: $KEY"
fi
# Adds the public half to the server's authorized_keys, unless it's already there
ssh "$HOST" 'read -r key; mkdir -p ~/.ssh; touch ~/.ssh/authorized_keys
  grep -qxF "$key" ~/.ssh/authorized_keys || echo "$key" >> ~/.ssh/authorized_keys' < "$KEY.pub"
echo "The key can log in to $HOST."

# ---- 2. Look up the values ----
EC2_HOST=$(ssh -G "$HOST" | awk '/^hostname /{print $2}')
if [ "$(ssh -G "$HOST" | awk '/^user /{print $2}')" != ubuntu ]; then
  echo "Note: the workflow logs in as 'ubuntu'; $HOST uses a different user." >&2
fi
# The server's host key, read over the SSH connection your laptop already trusts.
# The workflow only connects to a server that has this key.
EC2_HOST_KEY="$EC2_HOST $(ssh "$HOST" 'cut -d" " -f1,2 /etc/ssh/ssh_host_ed25519_key.pub')"

# Region, account and security group, from the EC2 instance metadata service
read -r AWS_REGION ACCOUNT_ID SECURITY_GROUP_ID < <(ssh "$HOST" bash -s <<'REMOTE' || true
set -euo pipefail
token=$(curl -sf -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
md() { curl -sf -H "X-aws-ec2-metadata-token: $token" "http://169.254.169.254/latest/$1"; }
mac=$(md meta-data/mac)
groups=$(md "meta-data/network/interfaces/macs/$mac/security-group-ids")
md dynamic/instance-identity/document | python3 -c \
  'import json, sys; d = json.load(sys.stdin); print(d["region"], d["accountId"], sys.argv[1])' "$(echo "$groups" | head -1)"
REMOTE
) || true
if [ -z "${SECURITY_GROUP_ID:-}" ]; then
  echo "Couldn't read the AWS details from the server's instance metadata." >&2
  exit 1
fi

env_value() { grep "^$1=" "$ROOT/ecom-frontend/.env.production" | cut -d= -f2-; }
SITE_URL=$(env_value VITE_FRONTEND_URL)
STRIPE_PUBLISHABLE_KEY=$(env_value VITE_STRIPE_PUBLISHABLE_KEY)

REPO=$(git -C "$ROOT" remote get-url origin | sed -E 's#^.*github\.com[:/]##; s#\.git$##')
ROLE_ARN="arn:aws:iam::$ACCOUNT_ID:role/$ROLE_NAME"

# ---- 3. GitHub settings ----
NAMES=(EC2_HOST EC2_HOST_KEY AWS_REGION AWS_SECURITY_GROUP_ID AWS_DEPLOY_ROLE_ARN SITE_URL STRIPE_PUBLISHABLE_KEY)
VALUES=("$EC2_HOST" "$EC2_HOST_KEY" "$AWS_REGION" "$SECURITY_GROUP_ID" "$ROLE_ARN" "$SITE_URL" "$STRIPE_PUBLISHABLE_KEY")

if command -v gh > /dev/null && gh auth status > /dev/null 2>&1; then
  echo "Saving the settings in github.com/$REPO ..."
  for i in "${!NAMES[@]}"; do
    gh variable set "${NAMES[$i]}" --repo "$REPO" --body "${VALUES[$i]}"
  done
  # The "production" environment: only master may deploy, and only it can read the SSH key
  echo '{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}' |
    gh api -X PUT "repos/$REPO/environments/production" --input - > /dev/null
  gh api -X POST "repos/$REPO/environments/production/deployment-branch-policies" \
    -f name=master -f type=branch > /dev/null 2>&1 || true   # fails harmlessly if already there
  gh secret set DEPLOY_SSH_KEY --repo "$REPO" --env production < "$KEY"
  echo "Done: 7 variables, the production environment and its DEPLOY_SSH_KEY secret."
else
  cat <<EOF

The gh CLI isn't set up, so enter these on github.com/$REPO yourself:

  Settings > Secrets and variables > Actions > Variables tab > New repository variable
EOF
  for i in "${!NAMES[@]}"; do
    printf '    %-24s %s\n' "${NAMES[$i]}" "${VALUES[$i]}"
  done
  cat <<EOF

  Settings > Environments > New environment, named: production
    Deployment branches and tags: Selected branches and tags > Add rule: master
    Environment secrets > Add secret, named DEPLOY_SSH_KEY, value: everything this prints,
    from -----BEGIN to END-----:   cat $KEY
EOF
fi

# ---- 4. AWS ----
cat <<EOF

Now in the AWS console (IAM), see deploy/README.md "Automatic deploys from GitHub" for the clicks.

Trust policy for the role $ROLE_NAME:
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "arn:aws:iam::$ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:$REPO:environment:production"
        }
      }
    }
  ]
}

Permissions policy (inline, on the same role):
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "OpenAndCloseSshDuringDeploys",
      "Effect": "Allow",
      "Action": ["ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress"],
      "Resource": "arn:aws:ec2:$AWS_REGION:$ACCOUNT_ID:security-group/$SECURITY_GROUP_ID"
    }
  ]
}
EOF

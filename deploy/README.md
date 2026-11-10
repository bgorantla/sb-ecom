# Deploying sb-ecom to AWS

One small EC2 instance runs everything; CloudFront in front of it gives free HTTPS.

```
browser ──HTTPS──> CloudFront (dxxxx.cloudfront.net) ──HTTP──> EC2 t4g.micro, Ubuntu 24.04
                                                                 Nginx :80
                                                                   ├─ /                 React build (/var/www/sb-ecom)
                                                                   └─ /api /images ...  Spring Boot 127.0.0.1:8080
                                                                                          └─ PostgreSQL 127.0.0.1:5432
```

The site and the API share one address, so the JWT cookie and CORS work with no code changes,
and the frontend is built with an empty `VITE_BACK_END_URL` (it calls `/api` on whatever host served it).

## Files

| File | Runs on | What it does |
|---|---|---|
| `server-setup.sh` | server, once | Swap, Java 21, PostgreSQL, Nginx, app user, `/etc/sb-ecom.env` with generated secrets, service + site |
| `deploy.sh` | laptop, every deploy | Builds jar + frontend, uploads, restarts, waits for the API |
| `copy-local-data.sh` | laptop, optional | Replaces the server DB and images with your local ones |
| `demo-setup.sh` | laptop, once | Turns on demo mode, adds the catalog, installs the nightly reset (runs `demo/install.sh` on the server) |
| `demo/seed_catalog.py` | server | Adds the products in `demo/catalog.json` through the REST API |
| `demo/save-snapshot.sh` | server | Saves the store as the state the nightly reset returns to |
| `demo/reset.sh` | server, nightly | Restores that snapshot (run by `sb-ecom-demo-reset.timer` at 08:00 UTC) |
| `change-password.sh` | laptop | Sets a user's password in the server DB (e.g. `admin`) |
| `set-env.sh` | laptop | Sets one value in `/etc/sb-ecom.env` (e.g. `STRIPE_WEBHOOK_SECRET`) and restarts the backend |
| `setup-github-deploy.sh` | laptop, once | Prepares automatic deploys from GitHub Actions (see below) |
| `sb-ecom.service` | server | systemd unit, heap sized for 1 GB RAM |
| `nginx-sb-ecom.conf` | server | Static files + reverse proxy |
| `frontend.env.production` | laptop | Template: copy to `ecom-frontend/.env.production` and fill in |

## Everyday commands

```bash
bash deploy/deploy.sh sb-ecom                                  # redeploy after code changes
ssh sb-ecom 'sudo journalctl -u sb-ecom -f'                    # live backend logs
ssh sb-ecom 'sudo systemctl restart sb-ecom'                   # restart after editing /etc/sb-ecom.env
ssh sb-ecom 'sudo -u postgres psql ecommerce'                  # SQL shell on the live DB
ssh sb-ecom 'sudo -u postgres pg_dump ecommerce' > backup.sql  # back up the live DB
ssh sb-ecom 'sudo /opt/sb-ecom/demo/reset.sh'                  # reset the demo store now
ssh sb-ecom 'sudo /opt/sb-ecom/demo/save-snapshot.sh'          # keep catalog changes you made as admin
```

The store goes back to its saved snapshot every night. If you change the catalog on purpose
(as your own `admin`), run `save-snapshot.sh` afterwards or the next reset will undo it.

## Stripe webhook

Checkout works without it, but the webhook also creates the order when a shopper pays and closes
the tab before Stripe sends them back to the site.

1. In the Stripe dashboard (test mode), open **Developers → Webhooks** and add an endpoint
   (newer dashboards call it an *event destination*):
   - URL: `https://<your-site>/api/webhooks/stripe`
   - event: `payment_intent.succeeded`
2. Copy its **signing secret** (`whsec_...`) and put it on the server:
   `bash deploy/set-env.sh sb-ecom STRIPE_WEBHOOK_SECRET`
3. Stripe's dashboard shows each delivery and our answer. 200 means received.

Until the secret is set, the endpoint answers 503 and nothing else changes.

## Automatic deploys from GitHub

`.github/workflows/ci.yml` tests every push. On master, once the tests pass, its `deploy` job runs
`deploy.sh` on a GitHub server, the same way you do from your laptop.

The server's security group should only let your own IP in over SSH (in the EC2 console, the
SSH rule's source is "My IP", not 0.0.0.0/0). So the job opens port 22 for its own IP while it
deploys, then closes it again. To change the security group it needs AWS permissions, and it gets them
without any AWS keys stored in GitHub: GitHub gives the job a signed token (OIDC) that says
"repository bgorantla/sb-ecom, environment production", and AWS swaps that for temporary
credentials of a role that can only edit that one security group's rules.

Until the steps below are done, the deploy job is skipped and only the tests run.

1. **Collect the settings** (from the project root):
   ```bash
   bash deploy/setup-github-deploy.sh sb-ecom
   ```
   It makes an SSH key just for GitHub and adds it to the server, reads the server's address,
   host key, AWS region, account and security group, and puts them in the repo's GitHub settings
   (7 variables, plus a `production` environment holding the SSH key, which only master can use).
   With the [gh CLI](https://cli.github.com) installed and `gh auth login` done, it does this for
   you; otherwise it prints what to enter on github.com. It also prints two policies for step 3.
2. **Let AWS trust GitHub** (once per AWS account). IAM → **Identity providers** → **Add provider**:
   OpenID Connect, provider URL `https://token.actions.githubusercontent.com`, audience
   `sts.amazonaws.com`.
3. **Create the role.** IAM → **Roles** → **Create role** → **Custom trust policy**, paste the
   trust policy from step 1, skip the permissions page, name it `github-actions-sb-ecom-deploy`.
   Open the new role → **Add permissions** → **Create inline policy** → **JSON**, paste the
   permissions policy from step 1, and name it `open-ssh-for-deploys`.
4. **Try it**: push to master, or open the repo's **Actions** tab → **CI/CD** → **Run workflow**.

Each deploy then shows on the repo's front page under **Deployments**, with a link to the site.
If a deploy fails, the job's log says which step; `ssh sb-ecom 'sudo journalctl -u sb-ecom -n 100'`
shows the backend's side. To turn automatic deploys off, delete the `EC2_HOST` variable.

## Where things live on the server

- App jar: `/opt/sb-ecom/app.jar`, uploaded product images: `/opt/sb-ecom/images/`
- Secrets and settings: `/etc/sb-ecom.env` (DB password, JWT secret, Stripe key, CloudFront URL,
  `DEMO_MODE`, `SEED_SAMPLE_USERS`, `ADMIN_PASSWORD` for when the admin account is first created)
- Demo snapshot and reset scripts: `/opt/sb-ecom/demo/`
- Frontend: `/var/www/sb-ecom/`
- Nginx site: `/etc/nginx/sites-available/sb-ecom`

## Costs (Mumbai, ap-south-1)

t4g.micro about $4.10/month, public IPv4 (Elastic IP) about $3.65, 10 GB gp3 under $1, CloudFront $0 within its
always-free 1 TB / 10 M requests. About $9/month, drawn from free-plan credits while you have them.
If you shut it down: terminate the instance **and release the Elastic IP**, which keeps billing on its own.

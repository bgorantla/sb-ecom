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

# E-Shop

A full-stack online store built with **Spring Boot** and **React**, running on **AWS**.
Shoppers browse and pay, sellers manage their own products, and admins run the catalog.

**Live site:** https://d1loonx3il11ot.cloudfront.net
· **API docs:** https://d1loonx3il11ot.cloudfront.net/swagger-ui/index.html

## Features

| Shoppers | Sellers | Admins |
|---|---|---|
| Browse, search, filter and sort products | Add and edit their own products | Dashboard with product, order and revenue totals |
| Cart and saved delivery addresses | Upload product images | Manage categories, products and sellers |
| Checkout with Stripe (test mode) | View orders and update their status | Update the status of any order |

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security with JWT (in a cookie), Spring Data JPA / Hibernate, PostgreSQL, Stripe API, springdoc OpenAPI |
| Frontend | React 19, Redux Toolkit, React Router 7, Tailwind CSS 4, Material UI, Vite 7 |
| Infrastructure | AWS EC2 (t4g.micro, Ubuntu 24.04), Amazon CloudFront, Nginx, systemd, Bash deploy scripts |

## Architecture

```mermaid
flowchart LR
    B[Browser] -->|HTTPS| CF[CloudFront]
    CF -->|HTTP| N[Nginx on EC2]
    N -->|/ static files| R[React build]
    N -->|/api, /images| S[Spring Boot :8080]
    S --> P[(PostgreSQL)]
    S -->|payments| ST[Stripe]
```

The site and the API share one address: Nginx serves the React build and forwards `/api` and `/images`
to Spring Boot. That keeps the login cookie same-origin, so no CORS setup is needed in production.
Spring Boot and PostgreSQL only listen on localhost. Everything runs on one small instance for about
$9/month; see [deploy/README.md](deploy/README.md).

## Changes beyond the course

This started as the project from the [EmbarkX Spring Boot course](https://github.com/EmbarkXOfficial/spring-boot-course).
Changes since then:

- **Deployed to AWS** on a single EC2 instance behind CloudFront, with scripts for server setup,
  one-command deploys, password changes and data copies ([deploy/](deploy/))
- **Moved secrets out of the code**: database credentials, JWT secret and Stripe key now come from
  environment variables
- **Fixed the frontend build**: added the missing Vite React config (pages crashed with
  "React is not defined") and moved the styles to Tailwind CSS v4 syntax
- **Replaced the course placeholders** with an About page describing the project, a real Contact page,
  a site footer, page metadata and a favicon

## Running it locally

You need Java 21, Node.js 20+ and PostgreSQL.

**Database**

```bash
createdb ecommerce          # tables are created on first start
```

**Backend** (port 8080)

```bash
cd sb-ecom
export DB_PASSWORD=your-postgres-password
export JWT_SECRET=$(openssl rand -base64 32)
export STRIPE_SECRET_KEY=sk_test_...
./mvnw spring-boot:run
```

`DB_URL` and `DB_USERNAME` default to `jdbc:postgresql://localhost:5432/ecommerce` and `postgres`.
On startup the app creates three demo users (`user1`, `seller1`, `admin`); their passwords are in
`WebSecurityConfig.java`.

**Frontend** (port 5173). Create `ecom-frontend/.env.development.local`:

```
VITE_BACK_END_URL=http://localhost:8080
VITE_FRONTEND_URL=http://localhost:5173
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...
```

then:

```bash
cd ecom-frontend
npm install
npm run dev
```

Pay with Stripe's test card `4242 4242 4242 4242`, any future expiry date and any CVC.

## Project layout

```
sb-ecom/          Spring Boot backend
ecom-frontend/    React frontend
deploy/           AWS server setup and deploy scripts
```

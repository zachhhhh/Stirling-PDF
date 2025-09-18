# Railway Deployment Guide

This guide walks through hosting Stirling-PDF on [Railway](https://railway.app) using the Dockerfile in this repository. The container connects to Supabase for Postgres and Stripe for billing.

## 1. Prerequisites
- Railway account (free tier is enough to start).
- Railway CLI (`npm i -g @railway/cli` or follow Railway’s install docs).
- Dockerfile is already set up for multi-stage Gradle builds (`railway.toml` selects it automatically).

## 2. One-time Railway project bootstrap
```bash
railway login            # opens a browser window
railway init             # choose existing project or create a new one
railway up               # first deploy; you can cancel once it finishes building
```
This creates the default service using the `Dockerfile`. Subsequent deploys use the same command.

## 3. Configure environment variables
Set these in the Railway dashboard (Project → Variables) or via CLI. Values match `env/.env.local`.

```bash
railway variables set \
  SUPABASE_PROJECT_ID=dvgmfsxkbddvbqlwhhjz \
  SUPABASE_URL=https://dvgmfsxkbddvbqlwhhjz.supabase.co \
  SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2Z21mc3hrYmRkdmJxbHdoaGp6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgxNTkzNTEsImV4cCI6MjA3MzczNTM1MX0.dc30_5UOS8PdYOoGEQJLXzbxnwIK8SIbT_g9ApiBmOA \
  SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2Z21mc3hrYmRkdmJxbHdoaGp6Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc1ODE1OTM1MSwiZXhwIjoyMDczNzM1MzUxfQ.nZBvq-9IGv2A-WSPBwCczR2XF8UuHkmCZWSNuUr1fAs \
  SUPABASE_DB_PASSWORD=guan1126 \
  SPRING_DATASOURCE_URL="jdbc:postgresql://db.dvgmfsxkbddvbqlwhhjz.supabase.co:6543/postgres?sslmode=require&prepareThreshold=0" \
  SPRING_DATASOURCE_USERNAME=postgres \
  SPRING_DATASOURCE_PASSWORD=guan1126 \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10 \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
  SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000 \
  SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=10000 \
  SPRING_PROFILES_ACTIVE=prod \
  BILLING_STRIPE_PUBLISHABLEKEY=pk_live_example \
  BILLING_STRIPE_SECRETKEY=sk_live_example \
  BILLING_STRIPE_WEBHOOKSECRET=whsec_placeholder
```
> ⚠️ These values are live keys/passwords. Rotate them if they have been exposed and only store them inside Railway’s secret store.

## 4. Deploy
```bash
railway up
```
Railway builds the Docker image (Gradle bootJar runs in the build stage) and starts the container. Logs stream in the CLI and the web console.

On first boot, Flyway applies `V2025_02_20_01__signup_tables.sql` to Supabase. Subsequent deploys validate the schema.

## 5. Post-deploy checklist
1. Open the deployed URL (Project → Deployments → Domains) and verify the app loads.
2. Hit `/actuator/health` to confirm the Spring context is healthy.
3. Run a test `/public/signup` to ensure Supabase tables receive records.
4. Configure Stripe webhook to point at `https://<railway-domain>/api/v1/billing/webhook`.
5. Use the `/account` billing UI to create a checkout session and open the customer portal (Stripe test mode first).

## 6. Scaling and monitoring
- Adjust resources in Railway Project → Settings → Resources. A 512MB or 1GB instance is sufficient to start.
- Railway provides metrics and logs by default; set alerts if needed.
- You can add a cron service for periodic tasks by creating a new Railway service with `railway up --service cron`.

With this setup, Railway builds and runs the Spring Boot container automatically; you only manage Supabase and Stripe credentials.

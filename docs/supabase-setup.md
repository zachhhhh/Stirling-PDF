# Supabase Deployment Guide

This guide shows how to back Stirling-PDF with Supabase-managed Postgres using environment variables for all credentials.

## 1. Export Secrets to Your Shell or Deployment
Gather the values from **Supabase → Project Settings → API/Database** and store them as environment variables. Never commit secrets to version control.

**Option A – `.env.local` helper (recommended for local dev)**

1. Copy the template: `cp env/.env.local.example env/.env.local`
2. Edit `env/.env.local` with your Supabase values (the sample already includes the current database password `guan1126`; change keys only if you rotate them later).
3. Load it when you work: `set -a; source env/.env.local; set +a`

**Option B – export manually**

```bash
export SUPABASE_PROJECT_ID="<project-ref>"                     # e.g. dvgmfsxkbddvbqlwhhjz
export SUPABASE_URL="https://$SUPABASE_PROJECT_ID.supabase.co"
export SUPABASE_ANON_KEY="<rotated-anon-key>"
export SUPABASE_SERVICE_ROLE_KEY="<rotated-service-role-key>"
export SUPABASE_DB_PASSWORD="<database-password>"
```

> In production, store these in the platform’s secret manager (Render, Railway, ECS, etc.).

## 2. Configure Spring Boot / Flyway via Environment Variables
Point Stirling-PDF at Supabase by setting the datasource values. Flyway will re-use the same JDBC connection.

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.$SUPABASE_PROJECT_ID.supabase.co:6543/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="$SUPABASE_DB_PASSWORD"

# Optional: tune the Hikari pool for Supabase connection limits
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10
export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
export SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=10000
```

If you deploy the app via Docker Compose or Kubernetes, inject these values as environment variables in the container spec.

## 3. Run the Flyway Migration (Automatic on First Boot)
With the environment set, start the application once so Flyway applies `V2025_02_20_01__signup_tables.sql`.

```bash
SPRING_PROFILES_ACTIVE=prod \
JAVA_HOME=/path/to/jdk17 \
./gradlew :common:bootRun
```

Verify in the Supabase dashboard (Database → Tables) that you now have:
- `signup_verification_tokens`
- `signup_rate_limit`
- `flyway_schema_history`

## 4. Validate SaaS Flows
1. Submit `/public/signup` – rows should appear in the new tables.
2. Complete the email verification – the tenant becomes active with plan defaults.
3. Use `/account` (billing section) to trigger Stripe checkout and customer portal calls – confirm plan updates persist.
4. Monitor Supabase logs, set alerts for connection usage or errors.

## 5. Deployment Tips
- Store `SUPABASE_*` and `SPRING_DATASOURCE_*` values in your hosting provider’s secret store.
- If you later use Supabase REST/Realtime, expose `SUPABASE_SERVICE_ROLE_KEY` to the backend only; the anon key is optional unless you ship the Supabase JS client.
- Enable Supabase backups or PITR once you move beyond prototyping.

With the credentials managed through environment variables, Stirling-PDF can run on Supabase without exposing sensitive data.

# SaaS Transformation Roadmap

This document captures the architectural direction and immediate follow-up work required to evolve Stirling-PDF from a self-hosted product into a multi-tenant SaaS offering.

## 1. What Changed in This Iteration
- **Tenant domain model** – Introduced `Tenant`, `TenantPlan`, and persisted plan metadata required for per-customer provisioning.
- **Request-scoped tenant resolution** – Added `TenantContextFilter` and `TenantResolver` to resolve tenants from headers or host names and expose the resolved tenant for downstream services. Resolution now respects the `saas.enabled` flag, defaulting to the primary tenant when SaaS is disabled.
- **Tenant-aware persistence** – Updated team/user flows so every query and mutation is scoped by the current tenant. Legacy data is migrated to the default tenant at startup via `TenantBootstrap`.
- **Configuration surface** – New `saas.*` properties let operators enable SaaS mode, configure headers, and set default quotas.
- **Admin APIs** – `/api/v1/admin/tenants` now provides CRUD operations for provisioning and maintaining tenant records.
- **Telemetry isolation** – Usage metrics and audit logging now tag every event with the active tenant, enabling per-tenant dashboards and reliable quota enforcement.
- **Quota enforcement** – Each request consumes the tenant’s monthly operation allotment (`monthlyOperationLimit`), automatically returning 429 responses when limits are exceeded.
- **Self-serve signup (beta)** – `/public/signup` accepts tenant name/email/password and provisions a tenant + first admin without operator intervention.
- **Stripe billing hooks (beta)** – `StripeBillingService` + `BillingController` expose checkout, customer portal, and webhook endpoints so plan changes can be driven from Stripe.

## 2. Activation Checklist
1. **Decide tenant resolution strategy**
   - Use `saas.domain` for subdomain-based tenancy (`tenant.example.com`).
   - Or rely on the `X-Tenant-Slug` header (configurable via `saas.tenant-header`).
2. **Configure defaults**
   - Override `saas.enabled=true` and adjust plan limits in your deployment settings file.
   - Seed Stripe (or your chosen billing provider) IDs into the new `Tenant` fields once billing is wired up.
3. **Provision tenants**
   - Use the admin API (`POST /api/v1/admin/tenants`) to create your first tenant; capture the returned ID/slug for DNS or header-based routing.
   - Alternatively expose `/public/signup` for self-service onboarding (see TODO email verification notes below).
4. **Database migration**
   - Allow Hibernate to auto-evolve the schema, or create explicit migrations that add `tenants`, `tenant_id` on team/audit tables, and the new audit indexes if you manage the schema manually.
   - Flyway will now create the signup verification + rate-limit tables automatically via [`db/migration/V2025_02_20_01__signup_tables.sql`](../app/proprietary/src/main/resources/db/migration/V2025_02_20_01__signup_tables.sql). See the Supabase deployment guide if you are hosting on Supabase (`../docs/supabase-setup.md`).
   - If you prefer manual control, use the SQL in the **Tenant uniqueness migration** appendix below and [`docs/sql/2025-02-tenant-signup.sql`](../docs/sql/2025-02-tenant-signup.sql) before enabling self-service signup.
5. **Smoke test**
   - Start the server with `saas.enabled=true`, send requests with distinct `X-Tenant-Slug` values, and confirm team/user isolation.
6. **Email configuration**
   - Enable `mail.enabled=true`, set `mail.from`, `mail.fromName`, and `mail.resendApiKey` (or adapt `SignupVerificationService` to your transactional provider) so signup confirmations are dispatched automatically.
   - Use [`docs/settings.saas-template.yml`](../docs/settings.saas-template.yml) as a reference for Stripe, mail, and temp storage configuration in hosted environments.

## 3. Next Implementation Milestones
### Phase A – SaaS Foundations (in progress)
- [x] Tenant context & isolation for teams/users.
- [ ] Tenant-aware audit, metrics, and API key issuance.
- [ ] Tenant-level usage tracking (count operations, storage consumption).
- [x] Admin CLI/HTTP endpoints for provisioning tenants and assigning plans.

### Phase B – Monetisation & Growth
- Payment provider integration (Stripe Billing recommended).
- Subscription lifecycle webhooks → update `Tenant.plan`, limits, and status.
- Self-service onboarding flow (tenant signup, email verification, domain capture).
- Per-tenant rate limiting & feature gating driven off `TenantPlan`.

### Phase C – Operations & Reliability
- Tenant-scoped logging and observability dashboards.
- Background workers for scheduled usage resets and invoice reconciliation.
- Automated backups & restores per tenant with retention policies.
- Disaster recovery runbooks and region redundancy.

## 4. Developer Guidance
- Access the current tenant inside services using `TenantContext.getTenant()`; always fall back to the default tenant where appropriate.
- New repositories should expose tenant-scoped methods (`findByIdForTenant`, etc.) instead of global queries.
- When adding new features, ensure DTOs and API payloads never leak data across tenants. Prefer filtering in repositories rather than checking after fetch.
- Use the `TenantService` when provisioning entities so migration helpers stay consistent.

Keep this document updated as additional SaaS capabilities are delivered.

## 5. Billing Integration Quickstart
1. **Configure Stripe** in `settings.yml` (or the external settings path) under `billing`:
   ```yaml
   billing:
     stripe:
       secretKey: "sk_test_xxx"
       publishableKey: "pk_test_xxx"
       webhookSecret: "whsec_xxx"
     plans:
       FREE:
         priceId: price_free
       PRO:
         priceId: price_pro
         monthlyOperationLimit: 5000
         storageLimitMb: 10240
   ```
2. **Create checkout sessions** – call `POST /api/v1/billing/checkout` with a tenant id, target plan, and success/cancel URLs. Redirect end-users to the returned `url`.
3. **Expose the webhook** at `POST /api/v1/billing/webhook`. Stripe events (`checkout.session.completed`, `customer.subscription.*`) update tenant plan metadata automatically.
4. **Customer portal** – `POST /api/v1/billing/portal` yields a Stripe-hosted portal URL for admins to manage payment methods.
5. **Plan defaults** – `PlanService` applies plan limits to the tenant record; customise `billing.plans` to tune quotas per plan.

## Appendix – Tenant uniqueness migration (manual SQL)

Existing deployments should run the following SQL (adjust column/index names if your dialect differs) to backfill tenant identifiers and install the composite unique constraints before disabling `ddl-auto`:

```sql
-- Ensure a default tenant exists
INSERT INTO tenants (slug, display_name, plan, active, created_at, updated_at)
SELECT 'default', 'Default', 'FREE', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE slug = 'default');

-- Attach legacy teams to a tenant
ALTER TABLE teams ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
UPDATE teams SET tenant_id = (
    SELECT tenant_id FROM tenants WHERE slug = 'default'
) WHERE tenant_id IS NULL;
ALTER TABLE teams
    ADD CONSTRAINT fk_teams_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Attach legacy users, preferring the team tenant when available
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
UPDATE users u
SET tenant_id = COALESCE(
        (SELECT t.tenant_id FROM teams t WHERE t.team_id = u.team_id),
        (SELECT tenant_id FROM tenants WHERE slug = 'default')
    )
WHERE u.tenant_id IS NULL;
ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Replace global uniqueness with tenant-scoped indexes
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_username;
ALTER TABLE users ADD CONSTRAINT uk_user_tenant_username UNIQUE (tenant_id, username);

ALTER TABLE teams DROP CONSTRAINT IF EXISTS uk_team_name;
ALTER TABLE teams ADD CONSTRAINT uk_team_tenant_name UNIQUE (tenant_id, name);

-- Enforce non-null tenants
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE teams ALTER COLUMN tenant_id SET NOT NULL;

-- Self-service signup persistence
CREATE TABLE IF NOT EXISTS signup_verification_tokens (
    token VARCHAR(128) PRIMARY KEY,
    tenant_slug VARCHAR(64) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_signup_token_tenant ON signup_verification_tokens (tenant_slug);
CREATE INDEX IF NOT EXISTS idx_signup_token_expires ON signup_verification_tokens (expires_at);

CREATE TABLE IF NOT EXISTS signup_rate_limit (
    client_key VARCHAR(128) PRIMARY KEY,
    last_attempt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_signup_throttle_last_attempt ON signup_rate_limit (last_attempt);
```

Take a backup before applying the script. After the migration, set `spring.jpa.hibernate.ddl-auto` to `validate` (or `none`) so Hibernate does not attempt to recreate the legacy constraints.

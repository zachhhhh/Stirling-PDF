# SaaS Transformation Roadmap

This document captures the architectural direction and immediate follow-up work required to evolve Stirling-PDF from a self-hosted product into a multi-tenant SaaS offering.

## 1. What Changed in This Iteration
- **Tenant domain model** – Introduced `Tenant`, `TenantPlan`, and persisted plan metadata required for per-customer provisioning.
- **Request-scoped tenant resolution** – Added `TenantContextFilter` and `TenantResolver` to resolve tenants from headers or host names and expose the resolved tenant for downstream services. Resolution now respects the `saas.enabled` flag, defaulting to the primary tenant when SaaS is disabled.
- **Tenant-aware persistence** – Updated team/user flows so every query and mutation is scoped by the current tenant. Legacy data is migrated to the default tenant at startup via `TenantBootstrap`.
- **Configuration surface** – New `saas.*` properties let operators enable SaaS mode, configure headers, and set default quotas.
- **Admin APIs** – `/api/v1/admin/tenants` now provides CRUD operations for provisioning and maintaining tenant records.
- **Telemetry isolation** – Usage metrics and audit logging now tag every event with the active tenant, enabling per-tenant dashboards and reliable quota enforcement.
- **Self-serve signup (beta)** – `/public/signup` accepts tenant name/email/password and provisions a tenant + first admin without operator intervention.

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
5. **Smoke test**
   - Start the server with `saas.enabled=true`, send requests with distinct `X-Tenant-Slug` values, and confirm team/user isolation.

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

# Storage Isolation & Background Tasks

The current file handling model uses globally shared directories (`templates/`, `static/`, and the process-level temp folder) for every tenant. To safely run Stirling-PDF as a hosted service we need to partition and maintain these resources per tenant.

## Objectives

1. **Tenant-scoped temporary files** – Derive subdirectories using the current tenant slug/ID for every upload, intermediate file, and pipeline artifact. All cleanup jobs should operate within the tenant boundary.
2. **Persisted exports** – Reports, pipeline results, and database backups must live under a tenant-specific root to prevent accidental disclosure. Access checks should validate the tenant before streaming any file.
3. **Lifecycle automation** – Add scheduled jobs to:
   - prune temporary files older than the configured retention window per tenant;
   - reset usage counters and storage tallies on the first of the month;
   - rotate encrypted backups with per-tenant retention settings.
4. **Observability** – Emit metrics for disk usage per tenant and alert when quotas are near exhaustion.

## Immediate Implementation Tasks

- Extend `TempFileManager` (or introduce a `TenantTempFileManager`) to accept the current tenant and create files under `<tmp>/<tenant-slug>/`.
- Update download/export controllers to resolve files through the tenant-aware manager instead of constructing paths manually.
- Create a Spring `@Scheduled` job that iterates tenants and performs:
  - temp directory cleanup;
  - monthly `TenantUsageRecord` reset (storage + operations);
  - backup rotation.
- Wire these jobs into the existing metrics pipeline so operations can monitor success/failures.

## Later Enhancements

- Automatically move large artifacts to object storage (S3, GCS) with per-tenant buckets or prefixes.
- Surface storage utilisation in the admin UI and expose APIs for customers to trigger manual cleanups.
- Add support for tiered retention policies driven by `TenantPlan`.

package stirling.software.proprietary.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.TenantPlan;
import stirling.software.proprietary.repository.TenantRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ApplicationProperties applicationProperties;

    @Transactional(readOnly = true)
    public Optional<Tenant> findBySlug(String slug) {
        return tenantRepository.findBySlugIgnoreCase(normalizeSlug(slug));
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findById(Long tenantId) {
        return tenantRepository.findById(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }

    @Transactional
    public Tenant getOrCreateDefaultTenant() {
        String defaultSlug = applicationProperties.getSaas().getDefaultTenantSlug();
        return tenantRepository
                .findBySlugIgnoreCase(defaultSlug)
                .orElseGet(
                        () -> {
                            log.info("Provisioning default tenant with slug '{}'.", defaultSlug);
                            Tenant tenant = new Tenant();
                            tenant.setSlug(defaultSlug);
                            tenant.setDisplayName(capitalize(defaultSlug));
                            tenant.setPlan(
                                    resolvePlan(applicationProperties.getSaas().getDefaultPlan()));
                            tenant.setMonthlyOperationLimit(
                                    applicationProperties
                                            .getSaas()
                                            .getDefaultMonthlyOperationLimit());
                            tenant.setStorageLimitMb(
                                    applicationProperties.getSaas().getDefaultStorageLimitMb());
                            tenant.setActive(true);
                            tenant.setTrialEndsAt(
                                    Instant.now()
                                            .plus(
                                                    applicationProperties
                                                            .getSaas()
                                                            .getTrialPeriod()));
                            return tenantRepository.save(tenant);
                        });
    }

    @Transactional
    public Tenant ensureTenantExists(String slug, String displayName, TenantPlan plan) {
        return tenantRepository
                .findBySlugIgnoreCase(normalizeSlug(slug))
                .orElseGet(
                        () -> {
                            Tenant tenant = new Tenant();
                            tenant.setSlug(normalizeSlug(slug));
                            tenant.setDisplayName(displayName);
                            tenant.setPlan(plan);
                            return tenantRepository.save(tenant);
                        });
    }

    @Transactional
    public Tenant createTenant(
            String slug,
            String displayName,
            String plan,
            Integer monthlyLimit,
            Integer storageLimitMb,
            Instant trialEndsAt,
            Boolean active,
            String billingCustomerId,
            String billingSubscriptionId) {
        String normalizedSlug = normalizeSlug(slug);
        if (tenantRepository.existsBySlugIgnoreCase(normalizedSlug)) {
            throw new IllegalArgumentException("Tenant slug already exists: " + normalizedSlug);
        }

        Tenant tenant = new Tenant();
        tenant.setSlug(normalizedSlug);
        tenant.setDisplayName(displayName);
        tenant.setPlan(resolvePlan(plan));
        tenant.setMonthlyOperationLimit(monthlyLimit);
        tenant.setStorageLimitMb(storageLimitMb);
        tenant.setTrialEndsAt(resolveTrialEndsAt(trialEndsAt));
        tenant.setActive(active == null ? true : active);
        tenant.setBillingCustomerId(billingCustomerId);
        tenant.setBillingSubscriptionId(billingSubscriptionId);
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant updateTenant(
            Long tenantId,
            String slug,
            String displayName,
            String plan,
            Integer monthlyLimit,
            Integer storageLimitMb,
            Instant trialEndsAt,
            Boolean active,
            String billingCustomerId,
            String billingSubscriptionId) {
        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Tenant not found: " + tenantId));

        if (slug != null && !slug.isBlank()) {
            String normalizedSlug = normalizeSlug(slug);
            if (!tenant.getSlug().equalsIgnoreCase(normalizedSlug)
                    && tenantRepository.existsBySlugIgnoreCase(normalizedSlug)) {
                throw new IllegalArgumentException("Tenant slug already exists: " + normalizedSlug);
            }
            tenant.setSlug(normalizedSlug);
        }

        if (displayName != null && !displayName.isBlank()) {
            tenant.setDisplayName(displayName);
        }

        if (plan != null && !plan.isBlank()) {
            tenant.setPlan(resolvePlan(plan));
        }

        if (monthlyLimit != null) {
            tenant.setMonthlyOperationLimit(monthlyLimit);
        }

        if (storageLimitMb != null) {
            tenant.setStorageLimitMb(storageLimitMb);
        }

        if (trialEndsAt != null) {
            tenant.setTrialEndsAt(trialEndsAt);
        }

        if (active != null) {
            tenant.setActive(active);
        }

        if (billingCustomerId != null) {
            tenant.setBillingCustomerId(billingCustomerId);
        }

        if (billingSubscriptionId != null) {
            tenant.setBillingSubscriptionId(billingSubscriptionId);
        }

        return tenantRepository.save(tenant);
    }

    private TenantPlan resolvePlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return TenantPlan.FREE;
        }
        try {
            return TenantPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown plan '{}', defaulting to FREE.", plan);
            return TenantPlan.FREE;
        }
    }

    private Instant resolveTrialEndsAt(Instant requestedTrialEnd) {
        if (requestedTrialEnd != null) {
            return requestedTrialEnd;
        }
        ApplicationProperties.Saas saas = applicationProperties.getSaas();
        if (saas == null) {
            return null;
        }
        Instant now = Instant.now();
        if (saas.getTrialPeriod() == null
                || saas.getTrialPeriod().isNegative()
                || saas.getTrialPeriod().isZero()) {
            return null;
        }
        return now.plus(saas.getTrialPeriod());
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Default";
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private String normalizeSlug(String slug) {
        if (slug == null) {
            return null;
        }
        return slug.trim().toLowerCase();
    }
}

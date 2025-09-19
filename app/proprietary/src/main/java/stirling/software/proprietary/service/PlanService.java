package stirling.software.proprietary.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.TenantPlan;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final ApplicationProperties applicationProperties;

    @jakarta.annotation.PostConstruct
    void logPlanServiceInit() {
        ApplicationProperties.Billing billing = applicationProperties.getBilling();
        boolean hasStripe = billing != null && billing.getStripe() != null;
        log.info(
                "PlanService initialised. Billing plans configured: {}. Stripe config present: {}",
                billing != null && billing.getPlans() != null,
                hasStripe);
    }

    public PlanDefinition getPlanDefinition(TenantPlan plan) {
        if (plan == null) {
            return null;
        }
        ApplicationProperties.Billing billing = applicationProperties.getBilling();
        if (billing != null && billing.getPlans() != null) {
            ApplicationProperties.Billing.Plan override = billing.getPlans().get(plan.name());
            if (override != null) {
                Integer monthlyLimit =
                        firstNonNull(
                                override.getMonthlyOperationLimit(),
                                plan.getMonthlyOperationLimit());
                Integer storageLimit =
                        firstNonNull(override.getStorageLimitMb(), plan.getStorageLimitMb());
                String priceId = override.getPriceId();
                boolean requiresPayment = Boolean.TRUE.equals(override.getRequiresPaymentMethod());
                boolean allowTrial = Boolean.TRUE.equals(override.getAllowTrial());
                return new PlanDefinition(
                        plan, priceId, monthlyLimit, storageLimit, requiresPayment, allowTrial);
            }
        }
        return new PlanDefinition(
                plan,
                null,
                plan.getMonthlyOperationLimit(),
                plan.getStorageLimitMb(),
                false,
                false);
    }

    public void applyPlanDefaults(Tenant tenant) {
        if (tenant == null || tenant.getPlan() == null) {
            return;
        }
        PlanDefinition definition = getPlanDefinition(tenant.getPlan());
        if (definition == null) {
            return;
        }
        if (definition.monthlyOperationLimit() != null
                && (tenant.getMonthlyOperationLimit() == null
                        || tenant.getMonthlyOperationLimit() <= 0)) {
            tenant.setMonthlyOperationLimit(definition.monthlyOperationLimit());
        }
        if (definition.storageLimitMb() != null
                && (tenant.getStorageLimitMb() == null || tenant.getStorageLimitMb() <= 0)) {
            tenant.setStorageLimitMb(definition.storageLimitMb());
        }
        if (tenant.getTrialEndsAt() == null && definition.allowTrial()) {
            Duration trialDuration = resolveTrialDuration();
            if (!trialDuration.isZero() && !trialDuration.isNegative()) {
                tenant.setTrialEndsAt(Instant.now().plus(trialDuration));
            }
        }
    }

    public String resolveStripePriceId(TenantPlan plan) {
        PlanDefinition definition = getPlanDefinition(plan);
        return definition == null ? null : definition.stripePriceId();
    }

    public ApplicationProperties.Billing.Stripe getStripeConfiguration() {
        ApplicationProperties.Billing billing = applicationProperties.getBilling();
        return billing != null ? billing.getStripe() : null;
    }

    public Map<TenantPlan, PlanDefinition> getAllPlanDefinitions() {
        Map<TenantPlan, PlanDefinition> definitions = new EnumMap<>(TenantPlan.class);
        for (TenantPlan plan : TenantPlan.values()) {
            PlanDefinition definition = getPlanDefinition(plan);
            if (definition != null) {
                definitions.put(plan, definition);
            }
        }
        return definitions;
    }

    private Integer firstNonNull(Integer candidate, Integer fallback) {
        return candidate != null ? candidate : fallback;
    }

    private Duration resolveTrialDuration() {
        ApplicationProperties.Saas saas = applicationProperties.getSaas();
        return saas != null ? saas.getTrialPeriod() : Duration.ZERO;
    }

    public record PlanDefinition(
            TenantPlan plan,
            String stripePriceId,
            Integer monthlyOperationLimit,
            Integer storageLimitMb,
            boolean requiresPaymentMethod,
            boolean allowTrial) {}
}

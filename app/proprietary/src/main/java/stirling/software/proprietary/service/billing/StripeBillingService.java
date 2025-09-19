package stirling.software.proprietary.service.billing;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.TenantPlan;
import stirling.software.proprietary.service.PlanService;
import stirling.software.proprietary.service.PlanService.PlanDefinition;
import stirling.software.proprietary.service.TenantService;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeBillingService {

    @PostConstruct
    public void initStripe() {
        log.info("StripeBillingService initialized - checking Stripe config");
        try {
            stripeConfig();
            log.info("Stripe config loaded successfully");
        } catch (Exception e) {
            log.error("Error initializing Stripe: {}", e.getMessage(), e);
        }
    }

    private final PlanService planService;
    private final TenantService tenantService;

    private ApplicationProperties.Billing.Stripe stripeConfig() {
        ApplicationProperties.Billing.Stripe stripe = planService.getStripeConfiguration();
        if (stripe == null || !StringUtils.hasText(stripe.getSecretKey())) {
            throw new IllegalStateException("Stripe secret key is not configured");
        }
        com.stripe.Stripe.apiKey = stripe.getSecretKey();
        return stripe;
    }

    public Session createCheckoutSession(
            Tenant tenant, TenantPlan targetPlan, String successUrl, String cancelUrl)
            throws StripeException {
        stripeConfig();
        PlanDefinition definition = planService.getPlanDefinition(targetPlan);
        if (definition == null || !StringUtils.hasText(definition.stripePriceId())) {
            throw new IllegalStateException("Plan does not have an associated Stripe price id");
        }

        SessionCreateParams.Builder builder =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .putMetadata("tenantId", tenant.getId().toString())
                        .putMetadata("targetPlan", targetPlan.name())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(definition.stripePriceId())
                                        .setQuantity(1L)
                                        .build());

        if (StringUtils.hasText(tenant.getBillingCustomerId())) {
            builder.setCustomer(tenant.getBillingCustomerId());
        }

        SessionCreateParams.SubscriptionData.Builder subscriptionData =
                SessionCreateParams.SubscriptionData.builder();
        buildPlanMetadata(targetPlan).forEach(subscriptionData::putMetadata);
        builder.setSubscriptionData(subscriptionData.build());

        return Session.create(builder.build());
    }

    public com.stripe.model.billingportal.Session createCustomerPortalSession(
            Tenant tenant, String returnUrl) throws StripeException {
        stripeConfig();
        if (!StringUtils.hasText(tenant.getBillingCustomerId())) {
            throw new IllegalStateException(
                    "Tenant does not have an associated Stripe customer id");
        }
        return com.stripe.model.billingportal.Session.create(
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(tenant.getBillingCustomerId())
                        .setReturnUrl(returnUrl)
                        .build());
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("Missing payload");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new IllegalArgumentException("Missing Stripe signature header");
        }
        ApplicationProperties.Billing.Stripe stripe = stripeConfig();
        if (!StringUtils.hasText(stripe.getWebhookSecret())) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripe.getWebhookSecret());
        } catch (Exception e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid signature", e);
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (!deserializer.getObject().isPresent()) {
            log.warn("Unable to deserialize Stripe event payload for type {}", event.getType());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" ->
                    handleCheckoutCompleted((Session) deserializer.getObject().get());
            case "customer.subscription.created", "customer.subscription.updated" ->
                    handleSubscriptionUpdated((Subscription) deserializer.getObject().get());
            case "customer.subscription.deleted" ->
                    handleSubscriptionCancelled((Subscription) deserializer.getObject().get());
            default -> log.debug("Ignoring Stripe event type {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) {
            log.warn("Checkout session missing metadata");
            return;
        }
        String tenantId = metadata.get("tenantId");
        String targetPlan = metadata.get("targetPlan");
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(targetPlan)) {
            log.warn("Checkout session missing tenant metadata");
            return;
        }
        Tenant tenant = tenantService.findById(Long.parseLong(tenantId)).orElse(null);
        if (tenant == null) {
            log.warn("Tenant {} not found while handling checkout completion", tenantId);
            return;
        }
        tenant.setBillingCustomerId(session.getCustomer());
        tenant.setBillingSubscriptionId(session.getSubscription());
        tenant.setPlan(resolvePlan(targetPlan));
        tenant.setActive(true);
        planService.applyPlanDefaults(tenant);
        tenantService.save(tenant);
    }

    private void handleSubscriptionUpdated(Subscription subscription) {
        updateTenantFromSubscription(subscription, true);
    }

    private void handleSubscriptionCancelled(Subscription subscription) {
        updateTenantFromSubscription(subscription, false);
    }

    private void updateTenantFromSubscription(Subscription subscription, boolean active) {
        if (subscription == null) {
            return;
        }
        String customerId = subscription.getCustomer();
        if (!StringUtils.hasText(customerId)) {
            log.warn("Subscription update missing customer id");
            return;
        }
        Tenant tenant = tenantService.findByBillingCustomerId(customerId);
        if (tenant == null) {
            log.warn("No tenant associated with Stripe customer {}", customerId);
            return;
        }
        String planKey =
                subscription.getMetadata() != null
                        ? subscription.getMetadata().get("targetPlan")
                        : null;
        TenantPlan plan = resolvePlan(planKey);
        if (plan != null) {
            tenant.setPlan(plan);
        }
        tenant.setBillingSubscriptionId(subscription.getId());
        tenant.setActive(active);
        planService.applyPlanDefaults(tenant);
        tenantService.save(tenant);
    }

    private Map<String, String> buildPlanMetadata(TenantPlan plan) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("targetPlan", plan.name());
        return metadata;
    }

    private TenantPlan resolvePlan(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        try {
            return TenantPlan.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown plan {} received from Stripe metadata", name);
            return null;
        }
    }
}

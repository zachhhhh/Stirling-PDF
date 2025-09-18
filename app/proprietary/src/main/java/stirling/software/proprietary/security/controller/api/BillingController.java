package stirling.software.proprietary.security.controller.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.TenantPlan;
import stirling.software.proprietary.service.TenantService;
import stirling.software.proprietary.service.billing.StripeBillingService;
import stirling.software.proprietary.tenant.TenantContext;
import stirling.software.proprietary.tenant.TenantContext.TenantDescriptor;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeBillingService stripeBillingService;
    private final TenantService tenantService;

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) {
        try {
            Tenant tenant = tenantService.findById(request.tenantId()).orElse(null);
            if (tenant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Tenant not found"));
            }
            if (!tenantAccessAllowed(tenant.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tenant mismatch"));
            }
            TenantPlan plan = resolvePlan(request.plan());
            Session session =
                    stripeBillingService.createCheckoutSession(
                            tenant, plan, request.successUrl(), request.cancelUrl());
            return ResponseEntity.ok(Map.of("sessionId", session.getId(), "url", session.getUrl()));
        } catch (StripeException ex) {
            log.error("Stripe checkout error", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/portal")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> createPortalSession(@RequestBody PortalRequest request) {
        try {
            Tenant tenant = tenantService.findById(request.tenantId()).orElse(null);
            if (tenant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Tenant not found"));
            }
            if (!tenantAccessAllowed(tenant.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tenant mismatch"));
            }
            var session =
                    stripeBillingService.createCustomerPortalSession(tenant, request.returnUrl());
            return ResponseEntity.ok(Map.of("url", session.getUrl()));
        } catch (StripeException ex) {
            log.error("Stripe portal error", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        try {
            if (!StringUtils.hasText(signature)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            stripeBillingService.handleWebhook(payload, signature);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Rejected Stripe webhook: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private boolean tenantAccessAllowed(Long tenantId) {
        TenantDescriptor descriptor = TenantContext.getTenant();
        if (descriptor == null || descriptor.id() == null) {
            return true; // default/super-admin contexts
        }
        return descriptor.id().equals(tenantId);
    }

    private TenantPlan resolvePlan(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Plan is required");
        }
        try {
            return TenantPlan.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown plan: " + input);
        }
    }

    public record CheckoutRequest(
            Long tenantId, String plan, String successUrl, String cancelUrl) {}

    public record PortalRequest(Long tenantId, String returnUrl) {}
}

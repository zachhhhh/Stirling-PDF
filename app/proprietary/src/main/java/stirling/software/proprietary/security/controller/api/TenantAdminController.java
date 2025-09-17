package stirling.software.proprietary.security.controller.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.dto.TenantCreateRequest;
import stirling.software.proprietary.model.dto.TenantResponse;
import stirling.software.proprietary.model.dto.TenantUpdateRequest;
import stirling.software.proprietary.service.TenantService;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@Tag(name = "Tenants", description = "Tenant administration APIs")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantService tenantService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<TenantResponse> listTenants() {
        return tenantService.listTenants().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody TenantCreateRequest request) {
        Tenant tenant =
                tenantService.createTenant(
                        request.slug(),
                        request.displayName(),
                        request.plan(),
                        request.monthlyOperationLimit(),
                        request.storageLimitMb(),
                        resolveTrialEndsAt(request.trialEndsAt()),
                        request.active(),
                        request.billingCustomerId(),
                        request.billingSubscriptionId());
        return ResponseEntity.created(URI.create("/api/v1/admin/tenants/" + tenant.getId()))
                .body(toResponse(tenant));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PatchMapping("/{tenantId}")
    public TenantResponse updateTenant(
            @PathVariable Long tenantId, @Valid @RequestBody TenantUpdateRequest request) {
        Tenant tenant =
                tenantService.updateTenant(
                        tenantId,
                        request.slug(),
                        request.displayName(),
                        request.plan(),
                        request.monthlyOperationLimit(),
                        request.storageLimitMb(),
                        resolveTrialEndsAt(request.trialEndsAt()),
                        request.active(),
                        request.billingCustomerId(),
                        request.billingSubscriptionId());
        return toResponse(tenant);
    }

    private Instant resolveTrialEndsAt(Instant trialEndsAt) {
        return trialEndsAt;
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getDisplayName(),
                tenant.getPlan().name(),
                tenant.getMonthlyOperationLimit(),
                tenant.getStorageLimitMb(),
                tenant.getTrialEndsAt(),
                tenant.isActive(),
                tenant.getBillingCustomerId(),
                tenant.getBillingSubscriptionId(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}

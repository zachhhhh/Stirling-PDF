package stirling.software.proprietary.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantUpdateRequest(
        @Size(max = 64) String slug,
        @Size(max = 255) String displayName,
        String plan,
        Integer monthlyOperationLimit,
        Integer storageLimitMb,
        Instant trialEndsAt,
        Boolean active,
        String billingCustomerId,
        String billingSubscriptionId) {}

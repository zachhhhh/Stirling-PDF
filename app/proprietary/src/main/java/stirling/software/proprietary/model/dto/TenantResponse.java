package stirling.software.proprietary.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantResponse(
        Long id,
        String slug,
        String displayName,
        String plan,
        Integer monthlyOperationLimit,
        Integer storageLimitMb,
        Instant trialEndsAt,
        Boolean active,
        String billingCustomerId,
        String billingSubscriptionId,
        Instant createdAt,
        Instant updatedAt) {}

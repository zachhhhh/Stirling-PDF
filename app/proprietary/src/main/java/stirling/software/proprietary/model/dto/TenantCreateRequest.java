package stirling.software.proprietary.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantCreateRequest(
        @NotBlank @Size(max = 64) String slug,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank String plan,
        Integer monthlyOperationLimit,
        Integer storageLimitMb,
        Instant trialEndsAt,
        Boolean active,
        String billingCustomerId,
        String billingSubscriptionId) {}

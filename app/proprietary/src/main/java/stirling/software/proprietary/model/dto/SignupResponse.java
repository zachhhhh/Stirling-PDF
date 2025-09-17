package stirling.software.proprietary.model.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SignupResponse {
    Long tenantId;
    String tenantSlug;
    String tenantName;
    String adminUsername;
    String plan;
}

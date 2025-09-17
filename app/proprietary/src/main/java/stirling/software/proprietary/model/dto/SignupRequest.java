package stirling.software.proprietary.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(
            regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]*$",
            message = "Slug may contain alphanumeric characters and hyphens")
    private String tenantSlug;

    @NotBlank
    @Size(max = 255)
    private String tenantName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String adminEmail;

    @NotBlank
    @Size(min = 8, max = 128)
    private String adminPassword;

    private String plan; // optional – defaults handled server side
}

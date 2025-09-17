package stirling.software.SPDF.controller.api.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import stirling.software.common.licensing.PremiumLicenseService;
import stirling.software.common.licensing.PremiumLicenseStatus;

@RestController
@RequestMapping("/api/v1/admin/license")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative APIs")
public class LicenseStatusController {

    private final PremiumLicenseService licenseService;

    @GetMapping("/status")
    @Operation(summary = "Retrieve current license metadata.")
    public PremiumLicenseStatus getLicenseStatus() {
        return licenseService.getCurrentLicenseStatus();
    }
}

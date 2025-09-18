package stirling.software.proprietary.controller.publicapi;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import stirling.software.common.model.enumeration.Role;
import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.dto.SignupRequest;
import stirling.software.proprietary.model.dto.SignupResponse;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.security.service.UserService;
import stirling.software.proprietary.service.PlanService;
import stirling.software.proprietary.service.TenantService;

@RestController
@RequestMapping("/public/signup")
@RequiredArgsConstructor
public class PublicSignupController {

    private static final Set<String> RESERVED_SLUGS =
            Set.of("admin", "root", "default", "api", "internal", "system");

    private final TenantService tenantService;
    private final TeamRepository teamRepository;
    private final UserService userService;
    private final SignupRateLimiter signupRateLimiter;
    private final SignupVerificationService signupVerificationService;
    private final PlanService planService;

    @PostMapping
    public ResponseEntity<?> signup(
            HttpServletRequest servletRequest, @Valid @RequestBody SignupRequest request)
            throws Exception {
        signupRateLimiter.cleanup();

        String normalizedSlug = request.getTenantSlug().trim().toLowerCase(Locale.ROOT);
        if (normalizedSlug.isBlank() || RESERVED_SLUGS.contains(normalizedSlug)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String throttleKey = extractClientKey(servletRequest);
        if (!signupRateLimiter.tryAcquire(throttleKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        String tenantName = request.getTenantName().trim();

        Optional<Tenant> existing = tenantService.findBySlug(normalizedSlug);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (userService.usernameExistsIgnoreCaseAcrossTenants(request.getAdminEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Tenant tenant =
                tenantService.createTenant(
                        normalizedSlug,
                        tenantName,
                        request.getPlan(),
                        null,
                        null,
                        null,
                        false,
                        null,
                        null);

        Team defaultTeam = new Team();
        defaultTeam.setName(buildTeamName(tenantName, normalizedSlug));
        defaultTeam.setTenant(tenant);
        defaultTeam = teamRepository.save(defaultTeam);

        try {
            var admin =
                    userService.saveUser(
                            request.getAdminEmail(),
                            request.getAdminPassword(),
                            defaultTeam,
                            Role.ADMIN.getRoleId(),
                            true);
            userService.changeUserEnabled(admin, false);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create admin user: " + ex.getMessage()));
        }

        signupVerificationService.enqueueVerification(request, tenant);

        SignupResponse response =
                SignupResponse.builder()
                        .tenantId(tenant.getId())
                        .tenantSlug(tenant.getSlug())
                        .tenantName(tenant.getDisplayName())
                        .adminUsername(request.getAdminEmail())
                        .plan(tenant.getPlan().name())
                        .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifySignup(@RequestParam("token") String token) {
        SignupVerificationService.VerificationResult result =
                signupVerificationService.verifyToken(token);
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", result.error()));
        }

        Optional<Tenant> tenantOpt = tenantService.findBySlug(result.tenantSlug());
        if (tenantOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Tenant not found"));
        }
        Tenant tenant = tenantOpt.get();

        Optional<stirling.software.proprietary.security.model.User> userOpt =
                userService.findByUsernameIgnoreCase(result.adminEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Admin user not found"));
        }

        tenant.setActive(true);
        planService.applyPlanDefaults(tenant);
        tenantService.save(tenant);

        try {
            userService.changeUserEnabled(userOpt.get(), true);
        } catch (SQLException
                | stirling.software.common.model.exception.UnsupportedProviderException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to enable admin user"));
        }

        return ResponseEntity.ok(Map.of("status", "verified"));
    }

    private String buildTeamName(String tenantName, String tenantSlug) {
        String base = StringUtils.hasText(tenantName) ? tenantName : tenantSlug;
        return base + " (" + tenantSlug + ")";
    }

    private String extractClientKey(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }
}

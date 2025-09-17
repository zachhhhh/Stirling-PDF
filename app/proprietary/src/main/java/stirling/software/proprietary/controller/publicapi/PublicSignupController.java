package stirling.software.proprietary.controller.publicapi;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import stirling.software.common.model.enumeration.Role;
import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.dto.SignupRequest;
import stirling.software.proprietary.model.dto.SignupResponse;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.security.service.UserService;
import stirling.software.proprietary.service.TenantService;

@RestController
@RequestMapping("/public/signup")
@RequiredArgsConstructor
public class PublicSignupController {

    private final TenantService tenantService;
    private final TeamRepository teamRepository;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request)
            throws Exception {
        String normalizedSlug = request.getTenantSlug().toLowerCase();
        String tenantName = request.getTenantName().trim();

        Optional<Tenant> existing = tenantService.findBySlug(normalizedSlug);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (userService.usernameExistsIgnoreCase(request.getAdminEmail())) {
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
                        true,
                        null,
                        null);

        Team defaultTeam = new Team();
        defaultTeam.setName(buildTeamName(tenantName, normalizedSlug));
        defaultTeam.setTenant(tenant);
        defaultTeam = teamRepository.save(defaultTeam);

        userService.saveUser(
                request.getAdminEmail(),
                request.getAdminPassword(),
                defaultTeam,
                Role.ADMIN.getRoleId(),
                true);

        // TODO: trigger transactional email (verification / welcome) once mail provider is wired up

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

    private String buildTeamName(String tenantName, String tenantSlug) {
        String base = StringUtils.hasText(tenantName) ? tenantName : tenantSlug;
        return base + " (" + tenantSlug + ")";
    }
}

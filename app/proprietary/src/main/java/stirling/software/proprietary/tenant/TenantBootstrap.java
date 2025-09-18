package stirling.software.proprietary.tenant;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.service.TenantService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrap {

    private final TenantService tenantService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void hydrateTenants() {
        Tenant defaultTenant = tenantService.getOrCreateDefaultTenant();
        List<Team> teamsWithoutTenant = teamRepository.findByTenantIsNull();
        if (!teamsWithoutTenant.isEmpty()) {
            teamsWithoutTenant.forEach(team -> team.setTenant(defaultTenant));
            teamRepository.saveAll(teamsWithoutTenant);
            log.info(
                    "Attached {} legacy team(s) to default tenant '{}' (id={}).",
                    teamsWithoutTenant.size(),
                    defaultTenant.getSlug(),
                    defaultTenant.getId());
        }

        List<User> usersWithoutTenant = userRepository.findByTenantIsNull();
        if (usersWithoutTenant.isEmpty()) {
            return;
        }

        usersWithoutTenant.forEach(
                user -> {
                    Tenant tenant = defaultTenant;
                    Team team = user.getTeam();
                    if (team != null && team.getTenant() != null) {
                        tenant = team.getTenant();
                    }
                    user.setTenant(tenant);
                });
        userRepository.saveAll(usersWithoutTenant);
        log.info("Assigned tenant context to {} legacy user(s).", usersWithoutTenant.size());
    }
}

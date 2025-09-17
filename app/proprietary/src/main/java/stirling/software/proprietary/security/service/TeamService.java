package stirling.software.proprietary.security.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.service.TenantService;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TenantService tenantService;

    public static final String DEFAULT_TEAM_NAME = "Default";
    public static final String INTERNAL_TEAM_NAME = "Internal";

    public Team getOrCreateDefaultTeam() {
        Tenant tenant = resolveDefaultTenant();
        return teamRepository
                .findByNameForTenant(DEFAULT_TEAM_NAME, tenant.getId())
                .or(() -> teamRepository.findByNameForTenant(DEFAULT_TEAM_NAME, null))
                .map(team -> ensureTenantAssociation(team, tenant))
                .orElseGet(
                        () -> {
                            Team defaultTeam = new Team();
                            defaultTeam.setName(DEFAULT_TEAM_NAME);
                            defaultTeam.setTenant(tenant);
                            return teamRepository.save(defaultTeam);
                        });
    }

    public Team getOrCreateInternalTeam() {
        Tenant tenant = resolveDefaultTenant();
        return teamRepository
                .findByNameForTenant(INTERNAL_TEAM_NAME, tenant.getId())
                .or(() -> teamRepository.findByNameForTenant(INTERNAL_TEAM_NAME, null))
                .map(team -> ensureTenantAssociation(team, tenant))
                .orElseGet(
                        () -> {
                            Team internalTeam = new Team();
                            internalTeam.setName(INTERNAL_TEAM_NAME);
                            internalTeam.setTenant(tenant);
                            return teamRepository.save(internalTeam);
                        });
    }

    private Tenant resolveDefaultTenant() {
        return tenantService.getOrCreateDefaultTenant();
    }

    private Team ensureTenantAssociation(Team team, Tenant tenant) {
        if (team.getTenant() == null) {
            team.setTenant(tenant);
            return teamRepository.save(team);
        }
        return team;
    }
}

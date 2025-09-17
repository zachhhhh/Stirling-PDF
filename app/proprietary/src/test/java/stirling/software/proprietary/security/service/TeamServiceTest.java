package stirling.software.proprietary.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.security.repository.TeamRepository;
import stirling.software.proprietary.service.TenantService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TenantService tenantService;

    @InjectMocks private TeamService teamService;

    @BeforeEach
    void setup() {
        when(teamRepository.save(any(Team.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getDefaultTeam() {
        var team = new Team();
        team.setName("Marleyans");
        Tenant tenant = defaultTenant();

        when(tenantService.getOrCreateDefaultTenant()).thenReturn(tenant);
        when(teamRepository.findByNameForTenant(TeamService.DEFAULT_TEAM_NAME, tenant.getId()))
                .thenReturn(Optional.of(team));

        Team result = teamService.getOrCreateDefaultTeam();

        assertEquals(team, result);
    }

    @Test
    void createDefaultTeam_whenRepositoryIsEmpty() {
        String teamName = "Default";
        var defaultTeam = new Team();
        defaultTeam.setId(1L);
        defaultTeam.setName(teamName);
        Tenant tenant = defaultTenant();

        when(tenantService.getOrCreateDefaultTenant()).thenReturn(tenant);
        when(teamRepository.findByNameForTenant(teamName, tenant.getId()))
                .thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(defaultTeam);

        Team result = teamService.getOrCreateDefaultTeam();

        assertEquals(TeamService.DEFAULT_TEAM_NAME, result.getName());
    }

    @Test
    void getInternalTeam() {
        var team = new Team();
        team.setName("Eldians");
        Tenant tenant = defaultTenant();

        when(tenantService.getOrCreateDefaultTenant()).thenReturn(tenant);
        when(teamRepository.findByNameForTenant(TeamService.INTERNAL_TEAM_NAME, tenant.getId()))
                .thenReturn(Optional.of(team));

        Team result = teamService.getOrCreateInternalTeam();

        assertEquals(team, result);
    }

    @Test
    void createInternalTeam_whenRepositoryIsEmpty() {
        String teamName = "Internal";
        Team internalTeam = new Team();
        internalTeam.setId(2L);
        internalTeam.setName(teamName);
        Tenant tenant = defaultTenant();

        when(tenantService.getOrCreateDefaultTenant()).thenReturn(tenant);
        when(teamRepository.findByNameForTenant(teamName, tenant.getId()))
                .thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(internalTeam);

        Team result = teamService.getOrCreateInternalTeam();

        assertEquals(internalTeam, result);
    }

    private Tenant defaultTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("default");
        tenant.setDisplayName("Default");
        return tenant;
    }
}

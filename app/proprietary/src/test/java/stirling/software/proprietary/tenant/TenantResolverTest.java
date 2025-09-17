package stirling.software.proprietary.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.service.TenantService;

@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock private TenantService tenantService;
    @Mock private HttpServletRequest request;

    private ApplicationProperties applicationProperties;

    private TenantResolver tenantResolver;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        tenantResolver = new TenantResolver(tenantService, applicationProperties);
    }

    @Test
    void resolveTenant_returnsDefaultWhenSaasDisabled() {
        applicationProperties.getSaas().setEnabled(false);
        Tenant defaultTenant = new Tenant();
        defaultTenant.setId(1L);
        defaultTenant.setSlug("default");
        when(tenantService.getOrCreateDefaultTenant()).thenReturn(defaultTenant);

        Tenant resolved = tenantResolver.resolveTenant(request);

        assertEquals(defaultTenant, resolved);
        verify(tenantService).getOrCreateDefaultTenant();
    }

    @Test
    void resolveTenant_usesHeaderWhenPresent() {
        applicationProperties.getSaas().setEnabled(true);
        applicationProperties.getSaas().setTenantHeader("X-Tenant-Slug");
        Tenant tenant = new Tenant();
        tenant.setId(2L);
        tenant.setSlug("acme");
        when(request.getHeader("X-Tenant-Slug")).thenReturn("acme");
        when(tenantService.findBySlug(anyString())).thenReturn(java.util.Optional.of(tenant));

        Tenant resolved = tenantResolver.resolveTenant(request);

        assertEquals(tenant, resolved);
    }
}

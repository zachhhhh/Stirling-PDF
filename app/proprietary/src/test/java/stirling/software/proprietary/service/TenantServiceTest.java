package stirling.software.proprietary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.TenantPlan;
import stirling.software.proprietary.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private ApplicationProperties applicationProperties;
    @Mock private PlanService planService;

    @InjectMocks private TenantService tenantService;

    private ApplicationProperties.Saas saasProperties;

    @BeforeEach
    void setup() {
        saasProperties = new ApplicationProperties.Saas();
        saasProperties.setDefaultTenantSlug("default");
        saasProperties.setTrialPeriod(Duration.ofDays(14));
        lenient().when(applicationProperties.getSaas()).thenReturn(saasProperties);
        lenient().doAnswer(invocation -> null).when(planService).applyPlanDefaults(any());
    }

    @Test
    void createTenant_assignsDefaultsAndNormalisesSlug() {
        when(tenantRepository.existsBySlugIgnoreCase("acme".toLowerCase())).thenReturn(false);
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(
                        invocation -> {
                            Tenant tenant = invocation.getArgument(0);
                            tenant.setId(42L);
                            return tenant;
                        });

        Tenant tenant =
                tenantService.createTenant(
                        "Acme", "Acme Corp", "PRO", 1000, 2048, null, null, null, null);

        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant captured = tenantCaptor.getValue();
        assertEquals("acme", captured.getSlug());
        assertEquals(TenantPlan.PRO, captured.getPlan());
        assertNotNull(captured.getTrialEndsAt());
        assertEquals(42L, tenant.getId());
    }

    @Test
    void createTenant_duplicateSlugThrows() {
        when(tenantRepository.existsBySlugIgnoreCase("duplicate")).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tenantService.createTenant(
                                "duplicate",
                                "Duplicate",
                                "FREE",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
    }

    @Test
    void updateTenant_updatesSelectedFieldsAndValidatesSlug() {
        Tenant existing = new Tenant();
        existing.setId(1L);
        existing.setSlug("legacy");
        existing.setDisplayName("Legacy");
        existing.setPlan(TenantPlan.FREE);
        existing.setActive(true);
        Instant originalTrial = Instant.now();
        existing.setTrialEndsAt(originalTrial);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant updated =
                tenantService.updateTenant(
                        1L,
                        "New-Slug",
                        "New Name",
                        "ENTERPRISE",
                        999,
                        4096,
                        originalTrial.plus(Duration.ofDays(7)),
                        false,
                        "cust_123",
                        "sub_456");

        assertEquals("new-slug", updated.getSlug());
        assertEquals("New Name", updated.getDisplayName());
        assertEquals(TenantPlan.ENTERPRISE, updated.getPlan());
        assertEquals(999, updated.getMonthlyOperationLimit());
        assertEquals(4096, updated.getStorageLimitMb());
        assertEquals(false, updated.isActive());
        assertEquals("cust_123", updated.getBillingCustomerId());
        assertEquals("sub_456", updated.getBillingSubscriptionId());
    }

    @Test
    void updateTenant_slugConflictThrows() {
        Tenant existing = new Tenant();
        existing.setId(1L);
        existing.setSlug("legacy");
        existing.setDisplayName("Legacy");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tenantRepository.existsBySlugIgnoreCase("taken")).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tenantService.updateTenant(
                                1L, "taken", null, null, null, null, null, null, null, null));
    }

    @Test
    void getOrCreateDefaultTenant_reusesExisting() {
        Tenant existingDefault = new Tenant();
        existingDefault.setId(100L);
        existingDefault.setSlug("default");

        when(tenantRepository.findBySlugIgnoreCase(eq("default")))
                .thenReturn(Optional.of(existingDefault));

        Tenant tenant = tenantService.getOrCreateDefaultTenant();

        assertEquals(100L, tenant.getId());
    }
}

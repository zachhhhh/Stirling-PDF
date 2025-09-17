package stirling.software.common.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;

class UsageMetricsServiceTest {

    private UsageMetricsService service;

    @BeforeEach
    void setUp() {
        service = new UsageMetricsService();
    }

    @Test
    void snapshotReflectsRecordedOperations() {
        service.recordOperation("merge", 3, null);
        service.recordOperation("merge", 2, null);
        service.recordOperation("split", 1, null);

        UsageMetricsSnapshot snapshot = service.snapshot();

        assertEquals(3, snapshot.getTotalOperations());
        assertEquals(6, snapshot.getTotalFilesProcessed());
        assertEquals(2, snapshot.getEntries().size());
        assertEquals(1, snapshot.getTenants().size());

        UsageMetricEntry merge = snapshot.getEntries().stream().findFirst().orElseThrow();
        assertEquals("merge", merge.getOperation());
        assertEquals(2, merge.getInvocations());
        assertEquals(5, merge.getFilesProcessed());
        assertTrue(merge.getLastInvocation().isAfter(Instant.EPOCH));
    }

    @Test
    void resetClearsAggregates() {
        service.recordOperation("merge", 1, null);
        service.reset();

        UsageMetricsSnapshot snapshot = service.snapshot();
        assertEquals(0, snapshot.getTotalOperations());
        assertEquals(0, snapshot.getTotalFilesProcessed());
        assertTrue(snapshot.getEntries().isEmpty());
        assertTrue(snapshot.getTenants().isEmpty());
    }

    @Test
    void tenantIsolationProducesSeparateBuckets() {
        var tenantA = new TenantDescriptor(1L, "tenant-a");
        var tenantB = new TenantDescriptor(2L, "tenant-b");

        service.recordOperation("merge", 2, tenantA);
        service.recordOperation("merge", 1, tenantB);
        service.recordOperation("split", 3, tenantB);

        UsageMetricsSnapshot snapshot = service.snapshot();
        assertEquals(2, snapshot.getTenants().size());

        TenantUsageMetrics tenantMetrics =
                snapshot.getTenants().stream()
                        .filter(t -> "tenant-a".equals(t.getTenantSlug()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, tenantMetrics.getTotalOperations());
        assertEquals(2, tenantMetrics.getTotalFilesProcessed());

        TenantUsageMetrics tenantBMetrics =
                snapshot.getTenants().stream()
                        .filter(t -> "tenant-b".equals(t.getTenantSlug()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, tenantBMetrics.getTotalOperations());
        assertEquals(4, tenantBMetrics.getTotalFilesProcessed());
    }
}

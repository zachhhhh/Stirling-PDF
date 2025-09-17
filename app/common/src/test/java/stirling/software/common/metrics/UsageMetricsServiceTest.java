package stirling.software.common.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageMetricsServiceTest {

    private UsageMetricsService service;

    @BeforeEach
    void setUp() {
        service = new UsageMetricsService();
    }

    @Test
    void snapshotReflectsRecordedOperations() {
        service.recordOperation("merge", 3);
        service.recordOperation("merge", 2);
        service.recordOperation("split", 1);

        UsageMetricsSnapshot snapshot = service.snapshot();

        assertEquals(3, snapshot.getTotalOperations());
        assertEquals(6, snapshot.getTotalFilesProcessed());
        assertEquals(2, snapshot.getEntries().size());

        UsageMetricEntry merge = snapshot.getEntries().stream().findFirst().orElseThrow();
        assertEquals("merge", merge.getOperation());
        assertEquals(2, merge.getInvocations());
        assertEquals(5, merge.getFilesProcessed());
        assertTrue(merge.getLastInvocation().isAfter(Instant.EPOCH));
    }

    @Test
    void resetClearsAggregates() {
        service.recordOperation("merge", 1);
        service.reset();

        UsageMetricsSnapshot snapshot = service.snapshot();
        assertEquals(0, snapshot.getTotalOperations());
        assertEquals(0, snapshot.getTotalFilesProcessed());
        assertTrue(snapshot.getEntries().isEmpty());
    }
}

package stirling.software.common.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import stirling.software.common.tenant.TenantContextSupplier;
import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;

@Slf4j
@Component
public class UsageMetricsService {

    private static final class OperationStats {
        private final AtomicLong invocations = new AtomicLong();
        private final AtomicLong filesProcessed = new AtomicLong();
        private volatile Instant lastInvocation = Instant.EPOCH;
    }

    private static final class TenantStats {
        private final AtomicLong totalOperations = new AtomicLong();
        private final AtomicLong totalFilesProcessed = new AtomicLong();
        private final Map<String, OperationStats> operations = new ConcurrentHashMap<>();
    }

    private static final TenantDescriptor GLOBAL_TENANT = new TenantDescriptor(null, "global");

    private final Map<TenantDescriptor, TenantStats> tenantMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalOperations = new AtomicLong();
    private final AtomicLong totalFilesProcessed = new AtomicLong();
    private volatile TenantContextSupplier tenantContextSupplier;

    @Autowired(required = false)
    void setTenantContextSupplier(TenantContextSupplier supplier) {
        this.tenantContextSupplier = supplier;
    }

    public void recordOperation(
            String operation, long filesInvolved, TenantDescriptor tenantOverride) {
        if (operation == null || operation.isBlank()) {
            log.debug("Ignoring usage record with blank operation name");
            return;
        }
        long files = Math.max(0, filesInvolved);
        totalOperations.incrementAndGet();
        totalFilesProcessed.addAndGet(files);

        TenantDescriptor descriptor = selectTenant(tenantOverride);
        TenantStats tenantStats =
                tenantMetrics.computeIfAbsent(descriptor, key -> new TenantStats());
        tenantStats.totalOperations.incrementAndGet();
        tenantStats.totalFilesProcessed.addAndGet(files);

        OperationStats stats =
                tenantStats.operations.computeIfAbsent(operation, key -> new OperationStats());
        stats.invocations.incrementAndGet();
        stats.filesProcessed.addAndGet(files);
        stats.lastInvocation = Instant.now();
    }

    public UsageMetricsSnapshot snapshot() {
        Map<String, GlobalStat> global = new HashMap<>();
        List<TenantUsageMetrics> tenants = new ArrayList<>(tenantMetrics.size());
        for (Map.Entry<TenantDescriptor, TenantStats> entry : tenantMetrics.entrySet()) {
            TenantDescriptor tenant = entry.getKey();
            TenantStats stats = entry.getValue();

            List<UsageMetricEntry> entries = new ArrayList<>(stats.operations.size());
            for (Map.Entry<String, OperationStats> opEntry : stats.operations.entrySet()) {
                OperationStats opStats = opEntry.getValue();
                GlobalStat globalStat =
                        global.computeIfAbsent(opEntry.getKey(), key -> new GlobalStat());
                globalStat.invocations += opStats.invocations.get();
                globalStat.files += opStats.filesProcessed.get();
                if (opStats.lastInvocation.isAfter(globalStat.lastInvocation)) {
                    globalStat.lastInvocation = opStats.lastInvocation;
                }
                entries.add(
                        UsageMetricEntry.builder()
                                .operation(opEntry.getKey())
                                .invocations(opStats.invocations.get())
                                .filesProcessed(opStats.filesProcessed.get())
                                .lastInvocation(opStats.lastInvocation)
                                .build());
            }
            entries.sort(Comparator.comparingLong(UsageMetricEntry::getInvocations).reversed());

            tenants.add(
                    TenantUsageMetrics.builder()
                            .tenantId(tenant.id())
                            .tenantSlug(tenant.slug())
                            .totalOperations(stats.totalOperations.get())
                            .totalFilesProcessed(stats.totalFilesProcessed.get())
                            .entries(entries)
                            .build());
        }

        tenants.sort(
                Comparator.comparing(
                        TenantUsageMetrics::getTenantSlug,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        List<UsageMetricEntry> aggregatedEntries = new ArrayList<>(global.size());
        for (Map.Entry<String, GlobalStat> entry : global.entrySet()) {
            GlobalStat stat = entry.getValue();
            aggregatedEntries.add(
                    UsageMetricEntry.builder()
                            .operation(entry.getKey())
                            .invocations(stat.invocations)
                            .filesProcessed(stat.files)
                            .lastInvocation(stat.lastInvocation)
                            .build());
        }
        aggregatedEntries.sort(
                Comparator.comparingLong(UsageMetricEntry::getInvocations).reversed());

        return UsageMetricsSnapshot.builder()
                .totalOperations(totalOperations.get())
                .totalFilesProcessed(totalFilesProcessed.get())
                .generatedAt(Instant.now())
                .entries(aggregatedEntries)
                .tenants(tenants)
                .build();
    }

    public void reset() {
        tenantMetrics.clear();
        totalOperations.set(0);
        totalFilesProcessed.set(0);
    }

    private TenantDescriptor selectTenant(TenantDescriptor tenantOverride) {
        if (tenantOverride != null) {
            return tenantOverride;
        }
        if (tenantContextSupplier != null) {
            return tenantContextSupplier.currentTenant().orElse(GLOBAL_TENANT);
        }
        return GLOBAL_TENANT;
    }

    private static final class GlobalStat {
        private long invocations;
        private long files;
        private Instant lastInvocation = Instant.EPOCH;
    }
}

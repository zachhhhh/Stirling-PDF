package stirling.software.common.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UsageMetricsService {

    private static final class OperationStats {
        private final AtomicLong invocations = new AtomicLong();
        private final AtomicLong filesProcessed = new AtomicLong();
        private volatile Instant lastInvocation = Instant.EPOCH;
    }

    private final Map<String, OperationStats> metrics = new ConcurrentHashMap<>();
    private final AtomicLong totalOperations = new AtomicLong();
    private final AtomicLong totalFilesProcessed = new AtomicLong();

    public void recordOperation(String operation, long filesInvolved) {
        if (operation == null || operation.isBlank()) {
            log.debug("Ignoring usage record with blank operation name");
            return;
        }
        long files = Math.max(0, filesInvolved);
        totalOperations.incrementAndGet();
        totalFilesProcessed.addAndGet(files);

        OperationStats stats = metrics.computeIfAbsent(operation, key -> new OperationStats());
        stats.invocations.incrementAndGet();
        stats.filesProcessed.addAndGet(files);
        stats.lastInvocation = Instant.now();
    }

    public UsageMetricsSnapshot snapshot() {
        List<UsageMetricEntry> entries = new ArrayList<>(metrics.size());
        for (Map.Entry<String, OperationStats> entry : metrics.entrySet()) {
            OperationStats stats = entry.getValue();
            entries.add(
                    UsageMetricEntry.builder()
                            .operation(entry.getKey())
                            .invocations(stats.invocations.get())
                            .filesProcessed(stats.filesProcessed.get())
                            .lastInvocation(stats.lastInvocation)
                            .build());
        }
        entries.sort(Comparator.comparingLong(UsageMetricEntry::getInvocations).reversed());

        return UsageMetricsSnapshot.builder()
                .totalOperations(totalOperations.get())
                .totalFilesProcessed(totalFilesProcessed.get())
                .generatedAt(Instant.now())
                .entries(entries)
                .build();
    }

    public void reset() {
        metrics.clear();
        totalOperations.set(0);
        totalFilesProcessed.set(0);
    }
}

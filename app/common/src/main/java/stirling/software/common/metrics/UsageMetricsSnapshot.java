package stirling.software.common.metrics;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UsageMetricsSnapshot {
    long totalOperations;
    long totalFilesProcessed;
    Instant generatedAt;
    List<UsageMetricEntry> entries;
}

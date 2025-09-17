package stirling.software.common.metrics;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TenantUsageMetrics {
    Long tenantId;
    String tenantSlug;
    long totalOperations;
    long totalFilesProcessed;
    List<UsageMetricEntry> entries;
}

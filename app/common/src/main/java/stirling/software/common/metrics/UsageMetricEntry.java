package stirling.software.common.metrics;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UsageMetricEntry {
    String operation;
    long invocations;
    long filesProcessed;
    Instant lastInvocation;
}

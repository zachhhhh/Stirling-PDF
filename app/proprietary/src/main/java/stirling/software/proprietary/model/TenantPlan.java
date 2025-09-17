package stirling.software.proprietary.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TenantPlan {
    FREE("Free", 250, 512),
    PRO("Pro", 5000, 10_240),
    ENTERPRISE("Enterprise", null, null);

    @Getter private final String displayName;

    /**
     * Maximum number of PDF operations that can be executed within a rolling 30 day window. A null
     * value indicates that the plan is unmetered and must be governed by policy instead of code.
     */
    @Getter private final Integer monthlyOperationLimit;

    /**
     * Storage allocation in megabytes reserved for generated artifacts, if persisted. Null means
     * unlimited (subject to operational policies).
     */
    @Getter private final Integer storageLimitMb;
}

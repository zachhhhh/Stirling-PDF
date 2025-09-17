package stirling.software.common.tenant;

import java.util.Optional;

/**
 * Abstraction used by shared infrastructure (metrics, audit, etc.) to resolve the tenant associated
 * with the current request. Implementations should return an empty Optional when multi-tenancy is
 * disabled or no tenant could be determined.
 */
@FunctionalInterface
public interface TenantContextSupplier {

    Optional<TenantDescriptor> currentTenant();

    record TenantDescriptor(
            Long id,
            String slug,
            String plan,
            Integer monthlyOperationLimit,
            Integer storageLimitMb) {}
}

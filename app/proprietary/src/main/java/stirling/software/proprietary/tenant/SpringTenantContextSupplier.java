package stirling.software.proprietary.tenant;

import java.util.Optional;

import org.springframework.stereotype.Component;

import stirling.software.common.tenant.TenantContextSupplier;

@Component
public class SpringTenantContextSupplier implements TenantContextSupplier {

    @Override
    public Optional<TenantDescriptor> currentTenant() {
        TenantContext.TenantDescriptor descriptor = TenantContext.getTenant();
        if (descriptor == null) {
            return Optional.empty();
        }
        return Optional.of(new TenantDescriptor(descriptor.id(), descriptor.slug()));
    }
}

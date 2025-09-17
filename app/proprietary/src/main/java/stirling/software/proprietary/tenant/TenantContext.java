package stirling.software.proprietary.tenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantContext {

    private static final ThreadLocal<TenantDescriptor> TENANT = new ThreadLocal<>();

    public static void setTenant(TenantDescriptor descriptor) {
        TENANT.set(descriptor);
    }

    public static TenantDescriptor getTenant() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }

    public record TenantDescriptor(Long id, String slug, String plan) {}
}

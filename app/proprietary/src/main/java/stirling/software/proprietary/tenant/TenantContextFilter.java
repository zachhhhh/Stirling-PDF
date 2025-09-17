package stirling.software.proprietary.tenant;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import stirling.software.proprietary.model.Tenant;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Tenant tenant = tenantResolver.resolveTenant(request);
        if (tenant != null) {
            TenantContext.setTenant(
                    new TenantContext.TenantDescriptor(
                            tenant.getId(),
                            tenant.getSlug(),
                            tenant.getPlan().name(),
                            tenant.getMonthlyOperationLimit(),
                            tenant.getStorageLimitMb()));
            response.setHeader("X-Tenant-Resolved", tenant.getSlug());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

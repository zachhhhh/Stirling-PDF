package stirling.software.proprietary.tenant;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.Tenant;

@Slf4j
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
            log.trace(
                    "Tenant resolved for request {} -> {}",
                    request.getRequestURI(),
                    tenant.getSlug());
            TenantContext.setTenant(
                    new TenantContext.TenantDescriptor(
                            tenant.getId(),
                            tenant.getSlug(),
                            tenant.getPlan().name(),
                            tenant.getMonthlyOperationLimit(),
                            tenant.getStorageLimitMb()));
            response.setHeader("X-Tenant-Resolved", tenant.getSlug());
        } else {
            log.trace("No tenant resolved for request {}", request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

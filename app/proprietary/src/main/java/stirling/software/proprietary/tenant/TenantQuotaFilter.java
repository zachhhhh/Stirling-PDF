package stirling.software.proprietary.tenant;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;
import stirling.software.proprietary.service.TenantUsageService;
import stirling.software.proprietary.service.exception.TenantQuotaExceededException;

@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class TenantQuotaFilter extends OncePerRequestFilter {

    private final TenantUsageService tenantUsageService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantDescriptor descriptor = mapDescriptor(TenantContext.getTenant());
        if (shouldEnforce(request, descriptor)) {
            try {
                tenantUsageService.consumeOperation(descriptor, 1);
            } catch (TenantQuotaExceededException ex) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Quota exceeded");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldEnforce(HttpServletRequest request, TenantDescriptor descriptor) {
        if (descriptor == null || descriptor.monthlyOperationLimit() == null) {
            return false;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/public/signup")) {
            return false;
        }
        return uri.startsWith("/api/") || uri.startsWith("/public/") || uri.startsWith("/pipeline");
    }

    private TenantDescriptor mapDescriptor(TenantContext.TenantDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        return new TenantDescriptor(
                descriptor.id(),
                descriptor.slug(),
                descriptor.plan(),
                descriptor.monthlyOperationLimit(),
                descriptor.storageLimitMb());
    }
}

package stirling.software.proprietary.tenant;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.service.TenantService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final TenantService tenantService;
    private final ApplicationProperties applicationProperties;

    public Tenant resolveTenant(HttpServletRequest request) {
        ApplicationProperties.Saas saasProps = applicationProperties.getSaas();
        if (saasProps == null || !saasProps.isEnabled()) {
            return tenantService.getOrCreateDefaultTenant();
        }
        String headerKey = saasProps.getTenantHeader();

        if (headerKey != null) {
            String tenantFromHeader = request.getHeader(headerKey);
            Tenant tenant = lookupTenant(tenantFromHeader, "header '" + headerKey + "'");
            if (tenant != null) {
                return tenant;
            }
        }

        String tenantFromParam = request.getParameter("tenant");
        Tenant tenant = lookupTenant(tenantFromParam, "query parameter 'tenant'");
        if (tenant != null) {
            return tenant;
        }

        String serverName = request.getServerName();
        if (StringUtils.hasText(serverName) && StringUtils.hasText(saasProps.getDomain())) {
            String domain = saasProps.getDomain();
            if (serverName.endsWith(domain)) {
                String potentialSubdomain =
                        serverName.substring(0, serverName.length() - domain.length());
                potentialSubdomain = trimTrailingDot(potentialSubdomain);
                tenant = lookupTenant(potentialSubdomain, "host subdomain");
                if (tenant != null) {
                    return tenant;
                }
            }
        }

        Tenant defaultTenant = tenantService.getOrCreateDefaultTenant();
        if (log.isDebugEnabled()) {
            log.debug(
                    "Falling back to default tenant '{}' (id={}) for request {} {}",
                    defaultTenant.getSlug(),
                    defaultTenant.getId(),
                    request.getMethod(),
                    request.getRequestURI());
        }
        return defaultTenant;
    }

    private String trimTrailingDot(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private Tenant lookupTenant(String candidate, String source) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        Optional<Tenant> tenant = tenantService.findBySlug(candidate.trim().toLowerCase());
        if (tenant.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("Resolved tenant '{}' from {}.", tenant.get().getSlug(), source);
            }
            return tenant.get();
        }
        log.warn("Tenant '{}' referenced via {} does not exist.", candidate, source);
        return null;
    }
}

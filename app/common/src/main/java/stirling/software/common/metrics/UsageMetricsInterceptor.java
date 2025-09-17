package stirling.software.common.metrics;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import stirling.software.common.tenant.TenantContextSupplier;
import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;

@Component
@RequiredArgsConstructor
public class UsageMetricsInterceptor implements HandlerInterceptor {

    private static final String ATTRIBUTE_FILE_COUNT =
            UsageMetricsInterceptor.class.getName() + ".files";
    private static final String ATTRIBUTE_OPERATION =
            UsageMetricsInterceptor.class.getName() + ".operation";
    private static final String ATTRIBUTE_TENANT =
            UsageMetricsInterceptor.class.getName() + ".tenant";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();
    private static final TenantContextSupplier NOOP_SUPPLIER = Optional::empty;
    private final UsageMetricsService usageMetricsService;
    private final ObjectProvider<TenantContextSupplier> tenantContextSupplier;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String pattern =
                (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            String lookupPath = PATH_HELPER.getLookupPathForRequest(request);
            pattern = lookupPath != null ? lookupPath : request.getRequestURI();
        }

        String operation = toOperationName(request, pattern);
        request.setAttribute(ATTRIBUTE_OPERATION, operation);
        request.setAttribute(ATTRIBUTE_FILE_COUNT, estimateFileCount(request));
        TenantDescriptor tenant =
                tenantContextSupplier
                        .getIfAvailable(() -> NOOP_SUPPLIER)
                        .currentTenant()
                        .orElse(null);
        if (tenant != null) {
            request.setAttribute(ATTRIBUTE_TENANT, tenant);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        Object op = request.getAttribute(ATTRIBUTE_OPERATION);
        if (op instanceof String operation && !operation.isBlank()) {
            Object filesAttr = request.getAttribute(ATTRIBUTE_FILE_COUNT);
            long files = filesAttr instanceof Number ? ((Number) filesAttr).longValue() : 0L;
            TenantDescriptor tenant = (TenantDescriptor) request.getAttribute(ATTRIBUTE_TENANT);
            usageMetricsService.recordOperation(operation, files, tenant);
        }
    }

    private long estimateFileCount(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            return multipartRequest.getMultiFileMap().values().stream()
                    .mapToLong(this::countMultipartFiles)
                    .sum();
        }
        return 0L;
    }

    private long countMultipartFiles(List<MultipartFile> files) {
        return files == null ? 0L : files.stream().filter(file -> !file.isEmpty()).count();
    }

    private String toOperationName(HttpServletRequest request, String pattern) {
        if (pattern == null) {
            return null;
        }
        String normalized = normalizePattern(pattern);
        String method = request.getMethod();
        return method + " " + normalized;
    }

    private String normalizePattern(String pattern) {
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1);
        }
        // Collapse multiple slashes
        pattern = pattern.replaceAll("/+", "/");

        // Remove file extension wildcards for clarity
        if (pattern.contains(".")) {
            pattern = pattern.replace(".*", "");
        }

        if (pattern.isBlank()) {
            return pattern;
        }

        // Example: api/v1/convert/** -> api/v1/convert
        if (pattern.endsWith("/**")) {
            pattern = pattern.substring(0, pattern.length() - 3);
        }
        if (pattern.endsWith("/*")) {
            pattern = pattern.substring(0, pattern.length() - 2);
        }

        return pattern;
    }
}

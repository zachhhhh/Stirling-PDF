package stirling.software.SPDF.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

import stirling.software.common.configuration.InstallationPathConfig;
import stirling.software.common.metrics.UsageMetricsInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EndpointInterceptor endpointInterceptor;
    private final UsageMetricsInterceptor usageMetricsInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(endpointInterceptor);
        registry.addInterceptor(usageMetricsInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Handler for external static resources
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "file:" + InstallationPathConfig.getStaticPath(), "classpath:/static/");
        // .setCachePeriod(0); // Optional: disable caching
    }
}

package stirling.software.SPDF.controller.api.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import stirling.software.common.metrics.UsageMetricsService;
import stirling.software.common.metrics.UsageMetricsSnapshot;

@RestController
@RequestMapping("/api/v1/admin/usage")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative APIs")
public class UsageMetricsController {

    private final UsageMetricsService usageMetricsService;

    @GetMapping("/snapshot")
    @Operation(summary = "Return the current aggregated usage metrics.")
    public UsageMetricsSnapshot snapshot() {
        return usageMetricsService.snapshot();
    }

    @PostMapping("/reset")
    @Operation(summary = "Clear in-memory usage metrics aggregates.")
    public void reset() {
        usageMetricsService.reset();
    }
}

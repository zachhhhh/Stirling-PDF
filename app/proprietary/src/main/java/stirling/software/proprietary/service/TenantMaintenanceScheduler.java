package stirling.software.proprietary.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.util.TempFileManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantMaintenanceScheduler {

    private final TempFileManager tempFileManager;
    private final TenantUsageService tenantUsageService;
    private final ApplicationProperties applicationProperties;

    @Scheduled(cron = "0 15 * * * *")
    public void cleanupTenantTempFiles() {
        if (!saasModeEnabled()) {
            return;
        }
        long maxAge = tempFileManager.getMaxAgeMillis();
        int deleted = tempFileManager.cleanupOldTempFiles(maxAge);
        if (deleted > 0) {
            log.info("Pruned {} tenant temp file(s) older than {} ms", deleted, maxAge);
        }
    }

    @Scheduled(cron = "0 5 1 * * *")
    public void purgeStaleUsageWindows() {
        if (!saasModeEnabled()) {
            return;
        }
        int purged = tenantUsageService.purgeObsoleteRecords();
        if (purged > 0) {
            log.info("Removed {} outdated tenant usage window(s)", purged);
        }
    }

    private boolean saasModeEnabled() {
        ApplicationProperties.Saas saas = applicationProperties.getSaas();
        return saas != null && saas.isEnabled();
    }
}

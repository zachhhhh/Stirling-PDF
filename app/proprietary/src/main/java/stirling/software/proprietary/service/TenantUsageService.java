package stirling.software.proprietary.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;
import stirling.software.proprietary.model.TenantUsageRecord;
import stirling.software.proprietary.repository.TenantUsageRecordRepository;
import stirling.software.proprietary.service.exception.TenantQuotaExceededException;

@Service
@RequiredArgsConstructor
public class TenantUsageService {

    private final TenantUsageRecordRepository usageRepository;

    @Transactional
    public void consumeOperation(TenantDescriptor tenant, long operations)
            throws TenantQuotaExceededException {
        if (tenant == null || tenant.id() == null) {
            return; // No tenant context -> skip enforcement
        }
        if (operations <= 0) {
            return;
        }

        LocalDate windowStart = currentWindowStart();
        TenantUsageRecord record =
                usageRepository
                        .findByTenantIdAndWindowStart(tenant.id(), windowStart)
                        .orElseGet(
                                () -> {
                                    TenantUsageRecord newRecord = new TenantUsageRecord();
                                    newRecord.setTenantId(tenant.id());
                                    newRecord.setTenantSlug(tenant.slug());
                                    newRecord.setWindowStart(windowStart);
                                    newRecord.setOperations(0);
                                    newRecord.setStorageMbUsed(0);
                                    Instant now = Instant.now();
                                    newRecord.setCreatedAt(now);
                                    newRecord.setUpdatedAt(now);
                                    return newRecord;
                                });

        long updatedOperations = record.getOperations() + operations;
        Integer limit = tenant.monthlyOperationLimit();
        if (limit != null && updatedOperations > limit) {
            throw new TenantQuotaExceededException(limit, updatedOperations);
        }

        record.setOperations(updatedOperations);
        record.setTenantSlug(tenant.slug());
        record.setUpdatedAt(Instant.now());
        usageRepository.save(record);
    }

    private LocalDate currentWindowStart() {
        Instant now = Instant.now();
        return now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
    }
}

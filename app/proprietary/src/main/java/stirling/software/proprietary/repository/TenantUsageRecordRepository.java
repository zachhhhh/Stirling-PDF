package stirling.software.proprietary.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import stirling.software.proprietary.model.TenantUsageRecord;

@Repository
public interface TenantUsageRecordRepository extends JpaRepository<TenantUsageRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantUsageRecord> findByTenantIdAndWindowStart(Long tenantId, LocalDate windowStart);

    @Modifying
    @Query("DELETE FROM TenantUsageRecord r WHERE r.windowStart < :windowStart")
    int deleteByWindowStartBefore(@Param("windowStart") LocalDate windowStart);
}

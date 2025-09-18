package stirling.software.proprietary.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import stirling.software.proprietary.model.SignupThrottleEntry;

@Repository
public interface SignupThrottleEntryRepository extends JpaRepository<SignupThrottleEntry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SignupThrottleEntry e WHERE e.clientKey = :clientKey")
    Optional<SignupThrottleEntry> findByClientKeyForUpdate(@Param("clientKey") String clientKey);

    @Transactional
    @Modifying
    @Query("DELETE FROM SignupThrottleEntry e WHERE e.lastAttempt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

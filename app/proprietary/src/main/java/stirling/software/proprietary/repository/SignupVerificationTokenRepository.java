package stirling.software.proprietary.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import stirling.software.proprietary.model.SignupVerificationToken;

@Repository
public interface SignupVerificationTokenRepository
        extends JpaRepository<SignupVerificationToken, String> {

    @Transactional
    @Modifying
    @Query("DELETE FROM SignupVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}

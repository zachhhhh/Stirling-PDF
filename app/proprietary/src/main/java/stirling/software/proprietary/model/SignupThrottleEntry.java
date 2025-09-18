package stirling.software.proprietary.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "signup_rate_limit",
        indexes = {@Index(name = "idx_signup_throttle_last_attempt", columnList = "last_attempt")})
@Getter
@Setter
public class SignupThrottleEntry {

    @Id
    @Column(name = "client_key", nullable = false, length = 128)
    private String clientKey;

    @Column(name = "last_attempt", nullable = false)
    private Instant lastAttempt;
}

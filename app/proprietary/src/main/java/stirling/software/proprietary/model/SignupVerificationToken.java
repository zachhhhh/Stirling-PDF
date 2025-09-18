package stirling.software.proprietary.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "signup_verification_tokens",
        indexes = {
            @Index(name = "idx_signup_token_tenant", columnList = "tenant_slug"),
            @Index(name = "idx_signup_token_expires", columnList = "expires_at")
        })
@Getter
@Setter
public class SignupVerificationToken {

    @Id
    @Column(name = "token", nullable = false, length = 128)
    private String token;

    @Column(name = "tenant_slug", nullable = false, length = 64)
    private String tenantSlug;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

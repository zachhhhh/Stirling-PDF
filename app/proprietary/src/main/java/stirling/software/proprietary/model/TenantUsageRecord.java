package stirling.software.proprietary.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "tenant_usage",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_tenant_usage_window",
                        columnNames = {"tenant_id", "window_start"}),
        indexes = {
            @Index(name = "idx_tenant_usage_tenant", columnList = "tenant_id"),
            @Index(name = "idx_tenant_usage_updated", columnList = "updated_at")
        })
@Getter
@Setter
public class TenantUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tenant_slug")
    private String tenantSlug;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "operations", nullable = false)
    private long operations;

    @Column(name = "storage_mb_used", nullable = false)
    private long storageMbUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

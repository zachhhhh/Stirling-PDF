package stirling.software.proprietary.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tenants")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 64)
    @EqualsAndHashCode.Include
    private String slug;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 32)
    private TenantPlan plan = TenantPlan.FREE;

    @Column(name = "monthly_operation_limit")
    private Integer monthlyOperationLimit;

    @Column(name = "storage_limit_mb")
    private Integer storageLimitMb;

    @Column(name = "billing_customer_id")
    private String billingCustomerId;

    @Column(name = "billing_subscription_id")
    private String billingSubscriptionId;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<Team> teams = new HashSet<>();

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addTeam(Team team) {
        teams.add(team);
        team.setTenant(this);
    }

    public void removeTeam(Team team) {
        teams.remove(team);
        team.setTenant(null);
    }
}

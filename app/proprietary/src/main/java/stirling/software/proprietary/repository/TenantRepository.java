package stirling.software.proprietary.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import stirling.software.proprietary.model.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);
}

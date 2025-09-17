package stirling.software.proprietary.security.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.model.dto.TeamWithUserCountDTO;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query(
            "SELECT t FROM Team t WHERE LOWER(t.name) = LOWER(:name) AND "
                    + "((:tenantId IS NULL AND t.tenant IS NULL) OR t.tenant.id = :tenantId)")
    Optional<Team> findByNameForTenant(
            @Param("name") String name, @Param("tenantId") Long tenantId);

    @Query(
            "SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t WHERE LOWER(t.name) = LOWER(:name) "
                    + "AND ((:tenantId IS NULL AND t.tenant IS NULL) OR t.tenant.id = :tenantId)")
    boolean existsByNameIgnoreCaseForTenant(
            @Param("name") String name, @Param("tenantId") Long tenantId);

    @Query(
            "SELECT t FROM Team t WHERE t.id = :teamId AND "
                    + "((:tenantId IS NULL AND t.tenant IS NULL) OR t.tenant.id = :tenantId)")
    Optional<Team> findByIdForTenant(
            @Param("teamId") Long teamId, @Param("tenantId") Long tenantId);

    @Query(
            "SELECT new stirling.software.proprietary.model.dto.TeamWithUserCountDTO(t.id, t.name, COUNT(u)) "
                    + "FROM Team t LEFT JOIN t.users u "
                    + "WHERE ((:tenantId IS NULL AND t.tenant IS NULL) OR t.tenant.id = :tenantId) "
                    + "GROUP BY t.id, t.name")
    List<TeamWithUserCountDTO> findAllTeamsWithUserCountForTenant(@Param("tenantId") Long tenantId);

    List<Team> findByTenantIsNull();
}

package stirling.software.proprietary.security.database.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import stirling.software.proprietary.model.Team;
import stirling.software.proprietary.security.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByUsernameIgnoreCaseAndTenantId(String username, Long tenantId);

    Optional<User> findByUsernameAndTenantId(String username, Long tenantId);

    @Query("FROM User u LEFT JOIN FETCH u.settings where upper(u.username) = upper(:username)")
    Optional<User> findByUsernameIgnoreCaseWithSettings(@Param("username") String username);

    @Query(
            "FROM User u LEFT JOIN FETCH u.settings where upper(u.username) = upper(:username)"
                    + " AND u.tenant.id = :tenantId")
    Optional<User> findByUsernameIgnoreCaseWithSettingsForTenant(
            @Param("username") String username, @Param("tenantId") Long tenantId);

    Optional<User> findByUsername(String username);

    Optional<User> findByApiKeyAndTenantId(String apiKey, Long tenantId);

    Optional<User> findByApiKey(String apiKey);

    List<User> findByAuthenticationTypeIgnoreCase(String authenticationType);

    @Query("SELECT u FROM User u WHERE u.team IS NULL")
    List<User> findAllWithoutTeam();

    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.team")
    List<User> findAllWithTeam();

    @Query(
            "SELECT u FROM User u JOIN FETCH u.authorities JOIN FETCH u.team WHERE u.team.id = :teamId")
    List<User> findAllByTeamId(@Param("teamId") Long teamId);

    long countByTeam(Team team);

    long countByTenantId(Long tenantId);

    List<User> findAllByTeam(Team team);

    List<User> findByTenantIsNull();

    boolean existsByUsernameIgnoreCaseAndTenantId(String username, Long tenantId);

    @Query(
            "SELECT u FROM User u WHERE u.team IS NULL AND"
                    + " (:tenantId IS NULL OR u.tenant.id = :tenantId)")
    List<User> findAllWithoutTeamForTenant(@Param("tenantId") Long tenantId);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.team WHERE"
                    + " (:tenantId IS NULL OR u.tenant.id = :tenantId)")
    List<User> findAllWithTeamForTenant(@Param("tenantId") Long tenantId);
}

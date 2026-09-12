package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from Account a
            left join fetch a.employee
            where a.id = :id
            """)
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUsername(String username);

    @Query("""
            select a
            from Account a
            left join a.employee e
            where (:query is null
                or lower(a.username) like lower(concat('%', :query, '%'))
                or lower(e.employeeCode) like lower(concat('%', :query, '%'))
                or lower(e.fullName) like lower(concat('%', :query, '%')))
              and (:role is null or a.role = :role)
              and (:status is null or a.status = :status)
            """)
    List<Account> search(
            @Param("query") String query,
            @Param("role") AccountRole role,
            @Param("status") AccountStatus status);
}

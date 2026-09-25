package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import scheduling.scheduling.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    List<Employee> findByStatusOrderByEmployeeCodeAsc(String status);

    @Query("""
            select e from Employee e
            where (:query is null
                or lower(e.employeeCode) like lower(concat('%', :query, '%'))
                or lower(e.fullName) like lower(concat('%', :query, '%')))
            and (:status is null or e.status = :status)
            order by e.employeeCode asc
            """)
    List<Employee> search(String query, String status);

    @Query("""
            select e from Employee e
            join fetch e.account a
            where e.status = 'ACTIVE'
              and a.role = scheduling.scheduling.entity.AccountRole.EMPLOYEE
              and a.status = scheduling.scheduling.entity.AccountStatus.ACTIVE
              and a.isActive = true
            order by e.employeeCode asc
            """)
    List<Employee> findSchedulableForAi();

    @Query("""
            select e from Employee e
            join fetch e.account a
            where e.status = 'ACTIVE'
              and a.role = scheduling.scheduling.entity.AccountRole.EMPLOYEE
              and a.status = scheduling.scheduling.entity.AccountStatus.ACTIVE
              and a.isActive = true
              and e.id <> :currentEmployeeId
            order by e.employeeCode asc, e.fullName asc
            """)
    List<Employee> findActiveCoworkers(Long currentEmployeeId);

    @Query("""
            select e from Employee e
            where e.status = 'ACTIVE'
            order by e.employeeCode asc, e.fullName asc
            """)
    List<Employee> findActiveForWorkloadReport();
}

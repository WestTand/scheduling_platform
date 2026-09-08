package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeCode(String employeeCode);
}

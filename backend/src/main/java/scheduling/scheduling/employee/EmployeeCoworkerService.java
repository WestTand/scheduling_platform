package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CoworkerResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class EmployeeCoworkerService {

    private final EmployeeAccess employeeAccess;
    private final EmployeeRepository employeeRepository;

    public EmployeeCoworkerService(EmployeeAccess employeeAccess, EmployeeRepository employeeRepository) {
        this.employeeAccess = employeeAccess;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<CoworkerResponse> listCoworkers(CurrentAccountPrincipal principal) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        return employeeRepository.findActiveCoworkers(employee.getId()).stream()
                .map(CoworkerResponse::from)
                .toList();
    }
}

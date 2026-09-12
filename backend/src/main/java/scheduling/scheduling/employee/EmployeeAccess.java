package scheduling.scheduling.employee;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;

@Service
public class EmployeeAccess {

    private final AccountRepository accountRepository;

    public EmployeeAccess(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Employee requireActiveEmployee(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee access required"));
        if (account.getRole() != AccountRole.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee access required");
        }
        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found");
        }
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee is inactive");
        }
        return employee;
    }
}

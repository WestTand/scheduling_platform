package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.admin.dto.UpdateEmployeeRequest;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class AdminEmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminEmployeeService(EmployeeRepository employeeRepository, RefreshTokenService refreshTokenService) {
        this.employeeRepository = employeeRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminEmployeeResponse> listEmployees(String query, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        if (normalizedQuery == null && normalizedStatus == null) {
            return employeeRepository.findAll().stream()
                    .map(AdminEmployeeResponse::from)
                    .toList();
        }
        return employeeRepository.search(normalizedQuery, normalizedStatus).stream()
                .map(AdminEmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminEmployeeResponse getEmployee(Long employeeId) {
        return AdminEmployeeResponse.from(findEmployee(employeeId));
    }

    @Transactional
    public AdminEmployeeResponse updateEmployee(Long employeeId, UpdateEmployeeRequest request) {
        Employee employee = findEmployee(employeeId);
        employee.setFullName(request.fullName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setStatus(request.status());
        return AdminEmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public void lockEmployee(Long employeeId) {
        Employee employee = findEmployee(employeeId);
        employee.setStatus("INACTIVE");
        Account account = employee.getAccount();
        if (account != null) {
            account.setIsActive(false);
            account.setStatus(AccountStatus.INACTIVE);
            refreshTokenService.revokeAllForAccount(account.getId());
        }
        employeeRepository.save(employee);
    }

    @Transactional
    public void unlockEmployee(Long employeeId) {
        Employee employee = findEmployee(employeeId);
        employee.setStatus("ACTIVE");
        Account account = employee.getAccount();
        if (account != null) {
            account.setIsActive(true);
            if (account.getStatus() == AccountStatus.INACTIVE) {
                account.setStatus(AccountStatus.ACTIVE);
            }
        }
        employeeRepository.save(employee);
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(EmployeeNotFoundException::new);
    }
}

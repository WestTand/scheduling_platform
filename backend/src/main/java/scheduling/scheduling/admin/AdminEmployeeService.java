package scheduling.scheduling.admin;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.admin.dto.CreateEmployeeRequest;
import scheduling.scheduling.admin.dto.UpdateEmployeeRequest;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.DuplicateResourceException;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class AdminEmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogService activityLogService;

    public AdminEmployeeService(
            EmployeeRepository employeeRepository,
            AccountRepository accountRepository,
            RefreshTokenService refreshTokenService,
            ActivityLogService activityLogService) {
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
        this.activityLogService = activityLogService;
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
        if (normalizedQuery == null) {
            return employeeRepository.findByStatusOrderByEmployeeCodeAsc(normalizedStatus).stream()
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
    public AdminEmployeeResponse createEmployee(CurrentAccountPrincipal principal, CreateEmployeeRequest request) {
        Account manager = requireManager(principal);
        String employeeCode = request.employeeCode();
        if (employeeRepository.existsByEmployeeCode(employeeCode)) {
            throw new DuplicateResourceException("Employee code already exists");
        }

        Employee employee = Employee.builder()
                .employeeCode(employeeCode)
                .fullName(request.fullName())
                .email(optionalValue(request.email()))
                .phone(optionalValue(request.phone()))
                .status(request.status())
                .build();
        Employee saved = employeeRepository.save(employee);
        activityLogService.record(manager, "CREATE", "employees", saved.getId(), null, employeeSnapshot(saved));
        return AdminEmployeeResponse.from(saved);
    }

    @Transactional
    public AdminEmployeeResponse updateEmployee(CurrentAccountPrincipal principal, Long employeeId, UpdateEmployeeRequest request) {
        Account manager = requireManager(principal);
        Employee employee = findEmployee(employeeId);
        employee.setFullName(request.fullName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setStatus(request.status());
        Employee saved = employeeRepository.save(employee);
        activityLogService.record(manager, "UPDATE", "employees", saved.getId(), null, employeeSnapshot(saved));
        return AdminEmployeeResponse.from(saved);
    }

    @Transactional
    public void lockEmployee(CurrentAccountPrincipal principal, Long employeeId) {
        Account manager = requireManager(principal);
        Employee employee = findEmployee(employeeId);
        employee.setStatus("INACTIVE");
        Account account = employee.getAccount();
        if (account != null) {
            account.setIsActive(false);
            if (account.getStatus() == AccountStatus.ACTIVE) {
                account.setStatus(AccountStatus.INACTIVE);
            }
            refreshTokenService.revokeAllForAccount(account.getId());
        }
        employeeRepository.save(employee);
        activityLogService.record(manager, "LOCK", "employees", employee.getId(), null, employeeSnapshot(employee));
    }

    @Transactional
    public void unlockEmployee(CurrentAccountPrincipal principal, Long employeeId) {
        Account manager = requireManager(principal);
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
        activityLogService.record(manager, "UNLOCK", "employees", employee.getId(), null, employeeSnapshot(employee));
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(EmployeeNotFoundException::new);
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER && account.getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private static String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private static String optionalValue(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static Map<String, Object> employeeSnapshot(Employee employee) {
        return Map.of(
                "id", employee.getId(),
                "employeeCode", employee.getEmployeeCode(),
                "fullName", employee.getFullName(),
                "status", employee.getStatus());
    }
}

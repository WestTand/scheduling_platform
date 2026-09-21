package scheduling.scheduling.auth;

import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.EmployeeNotFoundException;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class ManagerAccountService {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountActivationOtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final ActivityLogService activityLogService;
    private final String defaultEmployeePassword;

    public ManagerAccountService(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            AccountActivationOtpService otpService,
            RefreshTokenService refreshTokenService,
            ActivityLogService activityLogService,
            @Value("${app.auth.default-employee-password}") String defaultEmployeePassword) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.otpService = Objects.requireNonNull(otpService, "otpService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
        this.activityLogService = Objects.requireNonNull(activityLogService, "activityLogService");
        this.defaultEmployeePassword = Objects.requireNonNull(defaultEmployeePassword, "defaultEmployeePassword");
    }

    @Transactional
    public AccountResponse createEmployeeAccount(CurrentAccountPrincipal principal, CreateEmployeeAccountRequest request) {
        Account manager = requireManager(principal);
        String username = request.username().trim();
        if (accountRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists");
        }

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(EmployeeNotFoundException::new);
        if (employee.getAccount() != null) {
            throw new InvalidAccountStateException();
        }

        Account account = Account.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(defaultEmployeePassword))
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.PENDING_ACTIVATION)
                .employee(employee)
                .build();

        Account savedAccount = accountRepository.save(account);
        otpService.createActivationOtp(savedAccount);
        activityLogService.record(manager, "CREATE", "accounts", savedAccount.getId(), null, Map.of(
                "id", savedAccount.getId(),
                "username", savedAccount.getUsername(),
                "employeeId", employee.getId(),
                "status", savedAccount.getStatus().name()));

        return AccountResponse.from(savedAccount);
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

    @Transactional
    public void resendActivationOtp(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new InvalidAccountStateException();
        }

        otpService.createActivationOtp(account);
    }

    @Transactional
    public void resetPassword(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getRole() != AccountRole.EMPLOYEE) {
            throw new InvalidAccountStateException();
        }

        account.setPasswordHash(passwordEncoder.encode(defaultEmployeePassword));
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(accountId);
    }
}

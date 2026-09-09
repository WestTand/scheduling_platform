package scheduling.scheduling.auth;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final String defaultEmployeePassword;

    public ManagerAccountService(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            AccountActivationOtpService otpService,
            RefreshTokenService refreshTokenService,
            @Value("${app.auth.default-employee-password}") String defaultEmployeePassword) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.otpService = Objects.requireNonNull(otpService, "otpService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
        this.defaultEmployeePassword = Objects.requireNonNull(defaultEmployeePassword, "defaultEmployeePassword");
    }

    @Transactional
    public AccountResponse createEmployeeAccount(CreateEmployeeAccountRequest request) {
        if (accountRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new DuplicateResourceException("Employee code already exists");
        }

        Employee employee = Employee.builder()
                .employeeCode(request.employeeCode())
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .status("ACTIVE")
                .build();
        Employee savedEmployee = employeeRepository.save(employee);

        Account account = Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(defaultEmployeePassword))
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.PENDING_ACTIVATION)
                .employee(savedEmployee)
                .build();

        Account savedAccount = accountRepository.save(account);
        otpService.createActivationOtp(savedAccount);

        return AccountResponse.from(savedAccount);
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

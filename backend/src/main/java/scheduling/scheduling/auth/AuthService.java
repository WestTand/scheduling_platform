package scheduling.scheduling.auth;

import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.ActivationStateResponse;
import scheduling.scheduling.auth.dto.AuthResponse;
import scheduling.scheduling.auth.dto.EmployeeResponse;
import scheduling.scheduling.auth.dto.LoginRequest;
import scheduling.scheduling.auth.dto.LoginStateResponse;
import scheduling.scheduling.auth.dto.MeResponse;
import scheduling.scheduling.auth.dto.RegisterRequest;
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
import scheduling.scheduling.auth.dto.VerifyActivationOtpRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AccountActivationOtpService accountActivationOtpService;

    public AuthService(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AccountActivationOtpService accountActivationOtpService) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
        this.accountActivationOtpService = Objects.requireNonNull(accountActivationOtpService, "accountActivationOtpService");
    }

    @Transactional
    public AccountResponse register(RegisterRequest request) {
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
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .employee(savedEmployee)
                .build();

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(AuthenticationFailedException::new);
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new AuthenticationFailedException();
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        if (account.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            return LoginResult.state(new LoginStateResponse("OTP_REQUIRED", account.getId(), account.getUsername()));
        }
        if (account.getStatus() == AccountStatus.FORCE_PASSWORD_CHANGE) {
            return LoginResult.state(new LoginStateResponse("PASSWORD_CHANGE_REQUIRED", account.getId(), account.getUsername()));
        }

        account.setLastLogin(LocalDateTime.now());
        accountRepository.save(account);

        return loginResult(account, refreshTokenService.create(account));
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenResult refreshToken = refreshTokenService.rotate(rawRefreshToken);
        Account account = refreshToken.entity().getAccount();
        requireUsableAccount(account, "Invalid refresh token");
        return loginResult(account, refreshToken);
    }

    @Transactional
    public ActivationStateResponse verifyActivationOtp(VerifyActivationOtpRequest request) {
        Account account = accountActivationOtpService.verifyActivationOtp(request.accountId(), request.otp());
        return new ActivationStateResponse("PASSWORD_CHANGE_REQUIRED", account.getId(), account.getUsername());
    }

    @Transactional
    public void resendActivationOtp(ResendActivationOtpRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(AuthenticationFailedException::new);
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new InvalidAccountStateException();
        }
        if (!passwordEncoder.matches(request.temporaryPassword(), account.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        accountActivationOtpService.createActivationOtp(account);
    }

    @Transactional
    public LoginResult changeRequiredPassword(RequiredPasswordChangeRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(InvalidPasswordChangeStateException::new);
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.FORCE_PASSWORD_CHANGE) {
            throw new InvalidPasswordChangeStateException();
        }
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidPasswordChangeStateException();
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        return loginResult(account, refreshTokenService.create(account));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new InvalidTokenException("Invalid access token"));
        requireUsableAccount(account, "Invalid access token");
        Employee employee = account.getEmployee();
        return new MeResponse(AccountResponse.from(account), employee == null ? null : EmployeeResponse.from(employee));
    }

    private void requireUsableAccount(Account account, String message) {
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTokenException(message);
        }
    }

    private LoginResult loginResult(Account account, RefreshTokenResult refreshToken) {
        String accessToken = jwtService.createAccessToken(account.getId(), account.getUsername(), account.getRole());
        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                AccountResponse.from(account));
        return new LoginResult(response, refreshToken.rawToken());
    }
}

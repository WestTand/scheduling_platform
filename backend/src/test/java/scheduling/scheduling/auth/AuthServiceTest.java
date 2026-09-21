package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.ActivationStateResponse;
import scheduling.scheduling.auth.dto.AuthResponse;
import scheduling.scheduling.auth.dto.EmployeeResponse;
import scheduling.scheduling.auth.dto.LoginRequest;
import scheduling.scheduling.auth.dto.MeResponse;
import scheduling.scheduling.auth.dto.RegisterRequest;
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
import scheduling.scheduling.auth.dto.VerifyActivationOtpRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.RefreshToken;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

class AuthServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AccountActivationOtpService accountActivationOtpService = mock(AccountActivationOtpService.class);
    private final AuthService service = new AuthService(
            accountRepository,
            employeeRepository,
            passwordEncoder,
            jwtService,
            refreshTokenService,
            accountActivationOtpService);

    @Test
    void registerCreatesEmployeeAndEmployeeAccount() {
        RegisterRequest request = new RegisterRequest(
                "employee01",
                "plain-password",
                "EMP001",
                "Nguyen Van A",
                "employee01@example.com",
                "0900000001");
        Employee savedEmployee = employee("EMP001", "Nguyen Van A", "employee01@example.com", "0900000001");
        savedEmployee.setId(10L);
        Account savedAccount = account(20L, "employee01", "encoded-password", true, savedEmployee);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = service.register(request);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getEmployeeCode()).isEqualTo("EMP001");
        assertThat(employeeCaptor.getValue().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(employeeCaptor.getValue().getEmail()).isEqualTo("employee01@example.com");
        assertThat(employeeCaptor.getValue().getPhone()).isEqualTo("0900000001");
        assertThat(employeeCaptor.getValue().getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUsername()).isEqualTo("employee01");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(accountCaptor.getValue().getPasswordHash()).isNotEqualTo("plain-password");
        assertThat(accountCaptor.getValue().getRole()).isEqualTo(AccountRole.EMPLOYEE);
        assertThat(accountCaptor.getValue().getIsActive()).isTrue();
        assertThat(accountCaptor.getValue().getEmployee()).isSameAs(savedEmployee);
        assertThat(response).isEqualTo(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(accountRepository.existsByUsername("employee01")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmployeeCode() {
        when(employeeRepository.existsByEmployeeCode("EMP001")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Employee code already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(accountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        Account account = account(1L, "employee01", "hash", true, null);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("employee01", "wrong-password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");
        verify(accountRepository, never()).save(account);
    }

    @Test
    void loginRejectsInactiveAccount() {
        Account account = account(1L, "employee01", "hash", false, null);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(new LoginRequest("employee01", "password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    void loginCreatesAccessTokenAndRefreshToken() {
        Account account = account(1L, "employee01", "hash", true, null);
        RefreshToken refreshToken = RefreshToken.builder().account(account).build();
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(accountRepository.save(account)).thenReturn(account);
        when(jwtService.createAccessToken(1L, "employee01", AccountRole.EMPLOYEE)).thenReturn("access-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.create(account)).thenReturn(new RefreshTokenResult("refresh-token", refreshToken));

        LoginResult result = service.login(new LoginRequest("employee01", "password"));

        assertThat(account.getLastLogin()).isNotNull();
        verify(accountRepository).save(account);
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response()).isEqualTo(new AuthResponse(
                "access-token",
                "Bearer",
                900L,
                new AccountResponse(1L, "employee01", AccountRole.EMPLOYEE)));
    }

    @Test
    void loginReturnsOtpRequiredForPendingActivationAccountWithoutTokens() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        LoginResult result = service.login(new LoginRequest("employee01", "password"));

        assertThat(result.response()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.stateResponse().status()).isEqualTo("OTP_REQUIRED");
        assertThat(result.stateResponse().accountId()).isEqualTo(1L);
        assertThat(result.stateResponse().username()).isEqualTo("employee01");
        verify(accountRepository, never()).save(account);
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void loginReturnsPasswordChangeRequiredForForcePasswordChangeAccountWithoutTokens() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        LoginResult result = service.login(new LoginRequest("employee01", "password"));

        assertThat(result.response()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.stateResponse().status()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(result.stateResponse().accountId()).isEqualTo(1L);
        assertThat(result.stateResponse().username()).isEqualTo("employee01");
        verify(accountRepository, never()).save(account);
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void refreshRotatesRefreshTokenAndReturnsNewAccessToken() {
        Account account = account(1L, "employee01", "hash", true, null);
        RefreshToken refreshToken = RefreshToken.builder().account(account).build();
        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(new RefreshTokenResult("new-refresh-token", refreshToken));
        when(jwtService.createAccessToken(1L, "employee01", AccountRole.EMPLOYEE)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        LoginResult result = service.refresh("old-refresh-token");

        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.response()).isEqualTo(new AuthResponse(
                "new-access-token",
                "Bearer",
                900L,
                new AccountResponse(1L, "employee01", AccountRole.EMPLOYEE)));
    }

    @Test
    void refreshRejectsInactiveAccount() {
        Account account = account(1L, "employee01", "hash", false, null);
        RefreshToken refreshToken = RefreshToken.builder().account(account).build();
        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(new RefreshTokenResult("new-refresh-token", refreshToken));

        assertThatThrownBy(() -> service.refresh("old-refresh-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid refresh token");

        verify(jwtService, never()).createAccessToken(any(), any(), any());
    }

    @Test
    void refreshRejectsForcePasswordChangeAccount() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        RefreshToken refreshToken = RefreshToken.builder().account(account).build();
        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(new RefreshTokenResult("new-refresh-token", refreshToken));

        assertThatThrownBy(() -> service.refresh("old-refresh-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid refresh token");

        verify(jwtService, never()).createAccessToken(any(), any(), any());
    }

    @Test
    void logoutRevokesRefreshTokenWhenCookieExists() {
        service.logout("refresh-token");

        verify(refreshTokenService).revoke("refresh-token");
    }

    @Test
    void logoutDoesNothingForBlankOrNullRefreshToken() {
        service.logout(" ");
        service.logout(null);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void meReturnsAccountAndEmployee() {
        Employee employee = employee("EMP001", "Nguyen Van A", "employee01@example.com", "0900000001");
        employee.setId(10L);
        Account account = account(1L, "employee01", "hash", true, employee);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        MeResponse response = service.me(new CurrentAccountPrincipal(1L, "employee01", AccountRole.EMPLOYEE));

        assertThat(response.account()).isEqualTo(new AccountResponse(1L, "employee01", AccountRole.EMPLOYEE));
        assertThat(response.employee()).isEqualTo(new EmployeeResponse(
                10L,
                "EMP001",
                "Nguyen Van A",
                "employee01@example.com",
                "0900000001",
                "ACTIVE"));
    }

    @Test
    void meRejectsInactiveAccount() {
        Account account = account(1L, "employee01", "hash", false, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.me(new CurrentAccountPrincipal(1L, "employee01", AccountRole.EMPLOYEE)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void meRejectsPendingActivationAccount() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.me(new CurrentAccountPrincipal(1L, "employee01", AccountRole.EMPLOYEE)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid access token");
    }

    @Test
    void verifyActivationOtpReturnsPasswordChangeRequiredState() {
        Account account = account(42L, "employee01", "hash", true, null);
        VerifyActivationOtpRequest request = new VerifyActivationOtpRequest(42L, "123456");
        when(accountActivationOtpService.verifyActivationOtp(42L, "123456")).thenReturn(account);

        ActivationStateResponse response = service.verifyActivationOtp(request);

        assertThat(response).isEqualTo(new ActivationStateResponse("PASSWORD_CHANGE_REQUIRED", 42L, "employee01"));
    }

    @Test
    void changeRequiredPasswordActivatesAccountAndReturnsTokens() {
        Account account = account(42L, "employee01", "old-hash", true, null);
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        RequiredPasswordChangeRequest request = new RequiredPasswordChangeRequest(42L, "old-password", "new-password");
        RefreshToken refreshToken = RefreshToken.builder().account(account).build();
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(refreshTokenService.create(account)).thenReturn(new RefreshTokenResult("refresh-token", refreshToken));
        when(jwtService.createAccessToken(42L, "employee01", AccountRole.EMPLOYEE)).thenReturn("access-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        LoginResult result = service.changeRequiredPassword(request);

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).save(account);
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().account().id()).isEqualTo(42L);
    }

    @Test
    void changeRequiredPasswordRejectsWrongCurrentPassword() {
        Account account = account(42L, "employee01", "old-hash", true, null);
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "wrong-password", "new-password")))
                .isInstanceOf(InvalidPasswordChangeStateException.class)
                .hasMessage("Invalid password change state");

        verify(accountRepository, never()).save(account);
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void changeRequiredPasswordRejectsMissingAccount() {
        when(accountRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "old-password", "new-password")))
                .isInstanceOf(InvalidPasswordChangeStateException.class)
                .hasMessage("Invalid password change state");
    }

    @Test
    void changeRequiredPasswordRejectsInactiveAccount() {
        Account account = account(42L, "employee01", "old-hash", false, null);
        account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "old-password", "new-password")))
                .isInstanceOf(InvalidPasswordChangeStateException.class);
    }

    @Test
    void changeRequiredPasswordRejectsWrongStatus() {
        Account account = account(42L, "employee01", "old-hash", true, null);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "old-password", "new-password")))
                .isInstanceOf(InvalidPasswordChangeStateException.class);
    }

    @Test
    void resendActivationOtpCreatesOtpForPendingAccount() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("temp-password", "hash")).thenReturn(true);

        service.resendActivationOtp(new ResendActivationOtpRequest("employee01", "temp-password"));

        verify(accountActivationOtpService).createActivationOtp(account);
    }

    @Test
    void resendActivationOtpRejectsWrongPassword() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.resendActivationOtp(new ResendActivationOtpRequest("employee01", "wrong-password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(accountActivationOtpService, never()).createActivationOtp(any());
    }

    @Test
    void resendActivationOtpRejectsNonPendingAccount() {
        Account account = account(1L, "employee01", "hash", true, null);
        account.setStatus(AccountStatus.ACTIVE);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.resendActivationOtp(new ResendActivationOtpRequest("employee01", "temp-password")))
                .isInstanceOf(InvalidAccountStateException.class)
                .hasMessage("Invalid account state");

        verify(accountActivationOtpService, never()).createActivationOtp(any());
    }

    @Test
    void resendActivationOtpRejectsUnknownUsername() {
        when(accountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendActivationOtp(new ResendActivationOtpRequest("missing", "temp-password")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(accountActivationOtpService, never()).createActivationOtp(any());
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest("employee01", "password", "EMP001", "Nguyen Van A", null, null);
    }

    private static Employee employee(String employeeCode, String fullName, String email, String phone) {
        return Employee.builder()
                .employeeCode(employeeCode)
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .status("ACTIVE")
                .build();
    }

    private static Account account(Long id, String username, String passwordHash, boolean active, Employee employee) {
        Account account = Account.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role(AccountRole.EMPLOYEE)
                .isActive(active)
                .employee(employee)
                .build();
        account.setId(id);
        return account;
    }
}

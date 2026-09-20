package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

class ManagerAccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountActivationOtpService otpService = mock(AccountActivationOtpService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ManagerAccountService service = new ManagerAccountService(
            accountRepository,
            employeeRepository,
            passwordEncoder,
            otpService,
            refreshTokenService,
            activityLogService,
            "configured-default");

    @Test
    void createEmployeeAccountUsesDefaultPasswordAndPendingActivationStatus() {
        CreateEmployeeAccountRequest request = request();
        Employee savedEmployee = employee();
        savedEmployee.setId(10L);
        Account savedAccount = Account.builder()
                .username("employee01")
                .passwordHash("encoded-1111")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.PENDING_ACTIVATION)
                .isActive(true)
                .employee(savedEmployee)
                .build();
        savedAccount.setId(20L);
        Account manager = managerAccount();
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-1111");
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = service.createEmployeeAccount(principal, request);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getEmployeeCode()).isEqualTo("EMP001");
        assertThat(employeeCaptor.getValue().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(employeeCaptor.getValue().getEmail()).isEqualTo("employee01@gmail.com");
        assertThat(employeeCaptor.getValue().getPhone()).isEqualTo("0900000001");
        assertThat(employeeCaptor.getValue().getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUsername()).isEqualTo("employee01");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("encoded-1111");
        assertThat(accountCaptor.getValue().getRole()).isEqualTo(AccountRole.EMPLOYEE);
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(accountCaptor.getValue().getIsActive()).isTrue();
        assertThat(accountCaptor.getValue().getEmployee()).isSameAs(savedEmployee);
        verify(otpService).createActivationOtp(savedAccount);
        assertThat(response).isEqualTo(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));
    }

    @Test
    void createEmployeeAccountRecordsEmployeeCreateActivityLog() {
        CreateEmployeeAccountRequest request = request();
        Employee savedEmployee = employee();
        savedEmployee.setId(10L);
        Account savedAccount = Account.builder()
                .username("employee01")
                .passwordHash("encoded-1111")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.PENDING_ACTIVATION)
                .isActive(true)
                .employee(savedEmployee)
                .build();
        savedAccount.setId(20L);
        Account manager = managerAccount();
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-1111");
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        service.createEmployeeAccount(principal, request);

        verify(activityLogService).record(
                same(manager),
                eq("CREATE"),
                eq("employees"),
                eq(10L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(10L).equals(map.get("id"))
                        && "EMP001".equals(map.get("employeeCode"))
                        && "Nguyen Van A".equals(map.get("fullName"))
                        && "ACTIVE".equals(map.get("status"))));
    }

    @Test
    void createEmployeeAccountRejectsDuplicateUsername() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        when(accountRepository.existsByUsername("employee01")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployeeAccount(managerPrincipal(), request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createEmployeeAccountRejectsDuplicateEmployeeCode() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        when(employeeRepository.existsByEmployeeCode("EMP001")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployeeAccount(managerPrincipal(), request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Employee code already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void resendActivationOtpCreatesNewOtpForPendingActivationAccount() {
        Account account = account(20L, AccountStatus.PENDING_ACTIVATION, AccountRole.EMPLOYEE, true);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        service.resendActivationOtp(20L);

        verify(otpService).createActivationOtp(account);
    }

    @Test
    void resendActivationOtpRejectsMissingAccount() {
        when(accountRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendActivationOtp(20L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found");

        verifyNoInteractions(otpService);
    }

    @Test
    void resendActivationOtpRejectsNonPendingAccount() {
        Account account = account(20L, AccountStatus.ACTIVE, AccountRole.EMPLOYEE, true);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.resendActivationOtp(20L))
                .isInstanceOf(InvalidAccountStateException.class)
                .hasMessage("Invalid account state");

        verifyNoInteractions(otpService);
    }

    @Test
    void resetPasswordSetsDefaultPasswordForcePasswordChangeAndRevokesTokens() {
        Account account = account(20L, AccountStatus.ACTIVE, AccountRole.EMPLOYEE, true);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-default");

        service.resetPassword(20L);

        assertThat(account.getPasswordHash()).isEqualTo("encoded-default");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FORCE_PASSWORD_CHANGE);
        verify(accountRepository).save(account);
        verify(refreshTokenService).revokeAllForAccount(20L);
    }

    @Test
    void resetPasswordRejectsMissingAccount() {
        when(accountRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(20L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found");
    }

    @Test
    void resetPasswordRejectsManagerAccount() {
        Account account = account(20L, AccountStatus.ACTIVE, AccountRole.MANAGER, true);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.resetPassword(20L))
                .isInstanceOf(InvalidAccountStateException.class)
                .hasMessage("Invalid account state");

        verifyNoInteractions(refreshTokenService);
    }

    private static CreateEmployeeAccountRequest request() {
        return new CreateEmployeeAccountRequest(
                "employee01",
                "EMP001",
                "Nguyen Van A",
                "employee01@gmail.com",
                "0900000001");
    }

    private static Employee employee() {
        return Employee.builder()
                .employeeCode("EMP001")
                .fullName("Nguyen Van A")
                .email("employee01@gmail.com")
                .phone("0900000001")
                .status("ACTIVE")
                .build();
    }

    private static Account account(Long id, AccountStatus status, AccountRole role, boolean active) {
        Account account = Account.builder()
                .username("employee01")
                .passwordHash("old-hash")
                .role(role)
                .status(status)
                .isActive(active)
                .employee(employee())
                .build();
        account.setId(id);
        return account;
    }

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Account managerAccount() {
        Account account = Account.builder()
                .username("manager01")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(1L);
        return account;
    }
}

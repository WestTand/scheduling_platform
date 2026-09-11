package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.admin.dto.AdminAccountResponse;
import scheduling.scheduling.auth.AccountNotFoundException;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;

class AdminAccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AdminAccountService service = new AdminAccountService(accountRepository, refreshTokenService);

    @Test
    void listAccountsMapsEmployeeSummary() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        Account account = account(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true, employee);
        when(accountRepository.search(null, null, null)).thenReturn(List.of(account));

        List<AdminAccountResponse> response = service.listAccounts(null, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(20L);
        assertThat(response.getFirst().username()).isEqualTo("employee01");
        assertThat(response.getFirst().employee().id()).isEqualTo(10L);
        assertThat(response.getFirst().employee().employeeCode()).isEqualTo("EMP001");
    }

    @Test
    void listAccountsNormalizesFilters() {
        Account account = account(21L, "manager01", AccountRole.MANAGER, AccountStatus.ACTIVE, true, null);
        when(accountRepository.search("manager", AccountRole.MANAGER, AccountStatus.ACTIVE)).thenReturn(List.of(account));

        List<AdminAccountResponse> response = service.listAccounts(" manager ", "MANAGER", "ACTIVE");

        assertThat(response).extracting(AdminAccountResponse::username).containsExactly("manager01");
        verify(accountRepository).search("manager", AccountRole.MANAGER, AccountStatus.ACTIVE);
    }

    @Test
    void lockActiveAccountMarksInactiveAndRevokesTokens() {
        Account account = account(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true, null);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        service.lockAccount(20L);

        assertThat(account.getIsActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        verify(accountRepository).save(account);
        verify(refreshTokenService).revokeAllForAccount(20L);
    }

    @Test
    void lockPendingActivationAccountKeepsLifecycleStatus() {
        Account account = account(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.PENDING_ACTIVATION, true, null);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        service.lockAccount(20L);

        assertThat(account.getIsActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        verify(refreshTokenService).revokeAllForAccount(20L);
    }

    @Test
    void unlockInactiveAccountMarksActive() {
        Account account = account(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.INACTIVE, false, null);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        service.unlockAccount(20L);

        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).save(account);
    }

    @Test
    void unlockForcePasswordChangeAccountKeepsLifecycleStatus() {
        Account account = account(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.FORCE_PASSWORD_CHANGE, false, null);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));

        service.unlockAccount(20L);

        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FORCE_PASSWORD_CHANGE);
    }

    @Test
    void lockMissingAccountThrowsAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockAccount(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found");
    }

    private static Employee employee(Long id, String employeeCode, String fullName, String status) {
        Employee employee = Employee.builder()
                .employeeCode(employeeCode)
                .fullName(fullName)
                .email(employeeCode.toLowerCase() + "@example.com")
                .phone("0900000001")
                .status(status)
                .build();
        employee.setId(id);
        return employee;
    }

    private static Account account(Long id, String username, AccountRole role, AccountStatus status, boolean active, Employee employee) {
        Account account = Account.builder()
                .username(username)
                .passwordHash("hash")
                .role(role)
                .status(status)
                .isActive(active)
                .employee(employee)
                .build();
        account.setId(id);
        return account;
    }
}

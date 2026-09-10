package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.admin.dto.UpdateEmployeeRequest;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.EmployeeRepository;

class AdminEmployeeServiceTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AdminEmployeeService service = new AdminEmployeeService(employeeRepository, refreshTokenService);

    @Test
    void listEmployeesReturnsAllEmployeesWhenNoFilters() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        employee.setAccount(account(20L, "employee01", AccountStatus.ACTIVE, true));
        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        List<AdminEmployeeResponse> response = service.listEmployees(null, null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(10L);
        assertThat(response.getFirst().employeeCode()).isEqualTo("EMP001");
        assertThat(response.getFirst().fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getFirst().status()).isEqualTo("ACTIVE");
        assertThat(response.getFirst().account().id()).isEqualTo(20L);
        assertThat(response.getFirst().account().username()).isEqualTo("employee01");
    }

    @Test
    void listEmployeesSearchesCodeOrNameAndFiltersStatus() {
        Employee employee = employee(11L, "EMP002", "Tran Thi B", "INACTIVE");
        when(employeeRepository.search("tran", "INACTIVE")).thenReturn(List.of(employee));

        List<AdminEmployeeResponse> response = service.listEmployees(" tran ", "INACTIVE");

        assertThat(response).extracting(AdminEmployeeResponse::employeeCode).containsExactly("EMP002");
        assertThat(response).extracting(AdminEmployeeResponse::status).containsExactly("INACTIVE");
    }

    @Test
    void getEmployeeReturnsEmployeeWithAccountSummary() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        employee.setAccount(account(20L, "employee01", AccountStatus.FORCE_PASSWORD_CHANGE, true));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        AdminEmployeeResponse response = service.getEmployee(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.account().id()).isEqualTo(20L);
        assertThat(response.account().status()).isEqualTo(AccountStatus.FORCE_PASSWORD_CHANGE);
    }

    @Test
    void getEmployeeRejectsMissingEmployee() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployee(10L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessage("Employee not found");
    }

    @Test
    void updateEmployeeChangesEditableFields() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Nguyen Van A Updated",
                "employee01.updated@example.com",
                "0900000002",
                "INACTIVE");

        AdminEmployeeResponse response = service.updateEmployee(10L, request);

        assertThat(employee.getFullName()).isEqualTo("Nguyen Van A Updated");
        assertThat(employee.getEmail()).isEqualTo("employee01.updated@example.com");
        assertThat(employee.getPhone()).isEqualTo("0900000002");
        assertThat(employee.getStatus()).isEqualTo("INACTIVE");
        verify(employeeRepository).save(employee);
        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void updateEmployeeRejectsMissingEmployee() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.empty());
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Nguyen Van A Updated",
                "employee01.updated@example.com",
                "0900000002",
                "ACTIVE");

        assertThatThrownBy(() -> service.updateEmployee(10L, request))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessage("Employee not found");
    }

    @Test
    void lockEmployeeMarksEmployeeInactiveLocksAccountAndRevokesTokens() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        Account account = account(20L, "employee01", AccountStatus.ACTIVE, true);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.lockEmployee(10L);

        assertThat(employee.getStatus()).isEqualTo("INACTIVE");
        assertThat(account.getIsActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        verify(refreshTokenService).revokeAllForAccount(20L);
        verify(employeeRepository).save(employee);
    }

    @Test
    void unlockEmployeeMarksEmployeeActiveAndUnlocksAccount() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "INACTIVE");
        Account account = account(20L, "employee01", AccountStatus.INACTIVE, false);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.unlockEmployee(10L);

        assertThat(employee.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(employeeRepository).save(employee);
    }

    @Test
    void lockThenUnlockEmployeeDoesNotBypassPendingActivation() {
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        Account account = account(20L, "employee01", AccountStatus.PENDING_ACTIVATION, true);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.lockEmployee(10L);
        service.unlockEmployee(10L);

        assertThat(employee.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        verify(refreshTokenService).revokeAllForAccount(20L);
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

    private static Account account(Long id, String username, AccountStatus status, boolean active) {
        Account account = Account.builder()
                .username(username)
                .passwordHash("hash")
                .role(AccountRole.EMPLOYEE)
                .status(status)
                .isActive(active)
                .build();
        account.setId(id);
        return account;
    }
}

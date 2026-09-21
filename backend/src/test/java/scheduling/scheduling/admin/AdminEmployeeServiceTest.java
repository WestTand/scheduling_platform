package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

class AdminEmployeeServiceTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final AdminEmployeeService service =
            new AdminEmployeeService(employeeRepository, accountRepository, refreshTokenService, activityLogService);

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
    void createEmployeeSavesEmployeeAndRecordsActivityLog() {
        Account manager = givenManager();
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                " EMP003 ",
                " Le Van C ",
                " employee03@example.com ",
                " 0900000003 ",
                " ACTIVE ");
        when(employeeRepository.existsByEmployeeCode("EMP003")).thenReturn(false);
        when(employeeRepository.save(argThat(employee ->
                "EMP003".equals(employee.getEmployeeCode())
                        && "Le Van C".equals(employee.getFullName())
                        && "employee03@example.com".equals(employee.getEmail())
                        && "0900000003".equals(employee.getPhone())
                        && "ACTIVE".equals(employee.getStatus()))))
                .thenAnswer(invocation -> {
                    Employee saved = invocation.getArgument(0);
                    saved.setId(12L);
                    return saved;
                });

        AdminEmployeeResponse response = service.createEmployee(managerPrincipal(), request);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.employeeCode()).isEqualTo("EMP003");
        verify(activityLogService).record(
                same(manager),
                eq("CREATE"),
                eq("employees"),
                eq(12L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(12L).equals(map.get("id"))
                        && "EMP003".equals(map.get("employeeCode"))
                        && "Le Van C".equals(map.get("fullName"))
                        && "ACTIVE".equals(map.get("status"))));
    }

    @Test
    void createEmployeeRejectsDuplicateEmployeeCode() {
        givenManager();
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                " EMP003 ",
                " Le Van C ",
                " employee03@example.com ",
                " 0900000003 ",
                " ACTIVE ");
        when(employeeRepository.existsByEmployeeCode("EMP003")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(managerPrincipal(), request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Employee code already exists");

        verify(employeeRepository).existsByEmployeeCode("EMP003");
        verifyNoInteractions(activityLogService);
    }

    @Test
    void updateEmployeeChangesEditableFields() {
        givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Nguyen Van A Updated",
                "employee01.updated@example.com",
                "0900000002",
                "INACTIVE");

        AdminEmployeeResponse response = service.updateEmployee(managerPrincipal(), 10L, request);

        assertThat(employee.getFullName()).isEqualTo("Nguyen Van A Updated");
        assertThat(employee.getEmail()).isEqualTo("employee01.updated@example.com");
        assertThat(employee.getPhone()).isEqualTo("0900000002");
        assertThat(employee.getStatus()).isEqualTo("INACTIVE");
        verify(employeeRepository).save(employee);
        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void updateEmployeeRejectsMissingEmployee() {
        givenManager();
        when(employeeRepository.findById(10L)).thenReturn(Optional.empty());
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Nguyen Van A Updated",
                "employee01.updated@example.com",
                "0900000002",
                "ACTIVE");

        assertThatThrownBy(() -> service.updateEmployee(managerPrincipal(), 10L, request))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessage("Employee not found");

        verifyNoInteractions(activityLogService);
    }

    @Test
    void lockEmployeeMarksEmployeeInactiveLocksAccountAndRevokesTokens() {
        givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        Account account = account(20L, "employee01", AccountStatus.ACTIVE, true);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.lockEmployee(managerPrincipal(), 10L);

        assertThat(employee.getStatus()).isEqualTo("INACTIVE");
        assertThat(account.getIsActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        verify(refreshTokenService).revokeAllForAccount(20L);
        verify(employeeRepository).save(employee);
    }

    @Test
    void unlockEmployeeMarksEmployeeActiveAndUnlocksAccount() {
        givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "INACTIVE");
        Account account = account(20L, "employee01", AccountStatus.INACTIVE, false);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.unlockEmployee(managerPrincipal(), 10L);

        assertThat(employee.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(employeeRepository).save(employee);
    }

    @Test
    void lockThenUnlockEmployeeDoesNotBypassPendingActivation() {
        givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        Account account = account(20L, "employee01", AccountStatus.PENDING_ACTIVATION, true);
        employee.setAccount(account);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.lockEmployee(managerPrincipal(), 10L);
        service.unlockEmployee(managerPrincipal(), 10L);

        assertThat(employee.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        verify(refreshTokenService).revokeAllForAccount(20L);
    }

    @Test
    void updateEmployeeRecordsActivityLog() {
        Account manager = givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Nguyen Van A Updated",
                "employee01.updated@example.com",
                "0900000002",
                "INACTIVE");

        service.updateEmployee(managerPrincipal(), 10L, request);

        verify(activityLogService).record(
                same(manager),
                eq("UPDATE"),
                eq("employees"),
                eq(10L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(10L).equals(map.get("id"))
                        && "EMP001".equals(map.get("employeeCode"))
                        && "Nguyen Van A Updated".equals(map.get("fullName"))
                        && "INACTIVE".equals(map.get("status"))));
    }

    @Test
    void lockEmployeeRecordsActivityLog() {
        Account manager = givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "ACTIVE");
        employee.setAccount(account(20L, "employee01", AccountStatus.ACTIVE, true));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.lockEmployee(managerPrincipal(), 10L);

        verify(activityLogService).record(
                same(manager),
                eq("LOCK"),
                eq("employees"),
                eq(10L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(10L).equals(map.get("id"))
                        && "EMP001".equals(map.get("employeeCode"))
                        && "Nguyen Van A".equals(map.get("fullName"))
                        && "INACTIVE".equals(map.get("status"))));
    }

    @Test
    void unlockEmployeeRecordsActivityLog() {
        Account manager = givenManager();
        Employee employee = employee(10L, "EMP001", "Nguyen Van A", "INACTIVE");
        employee.setAccount(account(20L, "employee01", AccountStatus.INACTIVE, false));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        service.unlockEmployee(managerPrincipal(), 10L);

        verify(activityLogService).record(
                same(manager),
                eq("UNLOCK"),
                eq("employees"),
                eq(10L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(10L).equals(map.get("id"))
                        && "EMP001".equals(map.get("employeeCode"))
                        && "Nguyen Van A".equals(map.get("fullName"))
                        && "ACTIVE".equals(map.get("status"))));
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

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private Account givenManager() {
        Account manager = Account.builder()
                .username("manager01")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        manager.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        return manager;
    }
}

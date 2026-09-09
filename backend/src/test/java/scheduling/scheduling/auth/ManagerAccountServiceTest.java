package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final ManagerAccountService service = new ManagerAccountService(
            accountRepository,
            employeeRepository,
            passwordEncoder,
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
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-1111");
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = service.createEmployeeAccount(request);

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
        assertThat(response).isEqualTo(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));
    }

    @Test
    void createEmployeeAccountRejectsDuplicateUsername() {
        when(accountRepository.existsByUsername("employee01")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployeeAccount(request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createEmployeeAccountRejectsDuplicateEmployeeCode() {
        when(employeeRepository.existsByEmployeeCode("EMP001")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployeeAccount(request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Employee code already exists");

        verify(employeeRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
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
}

package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.EmployeeRepository;

class AdminEmployeeServiceTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final AdminEmployeeService service = new AdminEmployeeService(employeeRepository, null);

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

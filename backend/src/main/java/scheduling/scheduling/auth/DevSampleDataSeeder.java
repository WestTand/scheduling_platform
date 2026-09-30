package scheduling.scheduling.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@Component
@Profile("dev")
@Order(30)
public class DevSampleDataSeeder implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final String employeePassword;

    public DevSampleDataSeeder(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_EMPLOYEE_PASSWORD:Employee@123}") String employeePassword) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeePassword = employeePassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedEmployees();
    }

    private List<Employee> seedEmployees() {
        return employeeDefinitions().stream()
                .map(this::upsertEmployee)
                .toList();
    }

    private Employee upsertEmployee(EmployeeDefinition definition) {
        Employee employee = employeeRepository.findByEmployeeCode(definition.employeeCode())
                .orElseGet(() -> Employee.builder().employeeCode(definition.employeeCode()).build());
        employee.setFullName(definition.fullName());
        employee.setEmail(definition.email());
        employee.setPhone(definition.phone());
        employee.setStatus("ACTIVE");
        Employee savedEmployee = employeeRepository.save(employee);

        Account account = accountRepository.findByUsername(definition.email())
                .orElseGet(() -> Account.builder().username(definition.email()).build());
        account.setPasswordHash(passwordEncoder.encode(employeePassword));
        account.setRole(AccountRole.EMPLOYEE);
        account.setIsActive(true);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmployee(savedEmployee);
        accountRepository.save(account);

        return savedEmployee;
    }

    private static List<EmployeeDefinition> employeeDefinitions() {
        return List.of(
                new EmployeeDefinition("EMP001", "Nguyễn Văn An", "emp001@example.com", "0900000001"),
                new EmployeeDefinition("EMP002", "Trần Thị Bình", "emp002@example.com", "0900000002"),
                new EmployeeDefinition("EMP003", "Lê Minh Châu", "emp003@example.com", "0900000003"),
                new EmployeeDefinition("EMP004", "Phạm Quốc Dũng", "emp004@example.com", "0900000004"));
    }

    private record EmployeeDefinition(String employeeCode, String fullName, String email, String phone) {
    }
}

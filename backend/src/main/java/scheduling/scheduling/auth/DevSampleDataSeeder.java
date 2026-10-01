package scheduling.scheduling.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.seed.legacy-runners.enabled", havingValue = "true")
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
                new EmployeeDefinition("EMP004", "Phạm Quốc Dũng", "emp004@example.com", "0900000004"),
                new EmployeeDefinition("EMP005", "Hoàng Thu Hà", "emp005@example.com", "0900000005"),
                new EmployeeDefinition("EMP006", "Đỗ Minh Khang", "emp006@example.com", "0900000006"),
                new EmployeeDefinition("EMP007", "Vũ Ngọc Lan", "emp007@example.com", "0900000007"),
                new EmployeeDefinition("EMP008", "Ngô Đức Long", "emp008@example.com", "0900000008"),
                new EmployeeDefinition("EMP009", "Bùi Khánh Linh", "emp009@example.com", "0900000009"),
                new EmployeeDefinition("EMP010", "Đặng Quang Minh", "emp010@example.com", "0900000010"),
                new EmployeeDefinition("EMP011", "Phan Thảo My", "emp011@example.com", "0900000011"),
                new EmployeeDefinition("EMP012", "Tạ Hữu Nam", "emp012@example.com", "0900000012"),
                new EmployeeDefinition("EMP013", "Lý Bảo Ngọc", "emp013@example.com", "0900000013"),
                new EmployeeDefinition("EMP014", "Mai Hoài Phương", "emp014@example.com", "0900000014"),
                new EmployeeDefinition("EMP015", "Cao Thanh Sơn", "emp015@example.com", "0900000015"),
                new EmployeeDefinition("EMP016", "Trịnh Mai Trang", "emp016@example.com", "0900000016"),
                new EmployeeDefinition("EMP017", "Hà Tuấn Tú", "emp017@example.com", "0900000017"),
                new EmployeeDefinition("EMP018", "Lưu Gia Huy", "emp018@example.com", "0900000018"),
                new EmployeeDefinition("EMP019", "Nguyễn Hải Yến", "emp019@example.com", "0900000019"),
                new EmployeeDefinition("EMP020", "Chu Việt Anh", "emp020@example.com", "0900000020"));
    }

    private record EmployeeDefinition(String employeeCode, String fullName, String email, String phone) {
    }
}

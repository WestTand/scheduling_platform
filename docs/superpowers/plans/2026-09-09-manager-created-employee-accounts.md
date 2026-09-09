# Manager-Created Employee Accounts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ADMIN` role and `POST /api/manager/accounts` so managers/admins can create employee accounts with a default password loaded from configuration.

**Architecture:** Keep the existing auth service unchanged except for role enum compatibility. Add a focused manager account service and controller under the auth package, following existing DTO, exception, and MockMvc test patterns. Defer OTP/Gmail activation behavior to later phases.

**Tech Stack:** Spring Boot 4.1, Spring Security, Spring MVC, JPA entities, Lombok builders, JUnit 5, Mockito, AssertJ, MockMvc.

---

### Task 1: Add ADMIN Role

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/AccountRole.java`
- Modify: `backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java`

- [ ] **Step 1: Write the failing test**

Add this test to `EntityMappingTest`:

```java
@Test
void accountRolesIncludeSystemAdminManagerAndEmployee() {
    assertThat(AccountRole.values())
            .containsExactly(AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.EMPLOYEE);
    assertThat(AccountRole.ADMIN.getValue()).isEqualTo("ADMIN");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd "-Dtest=EntityMappingTest#accountRolesIncludeSystemAdminManagerAndEmployee" test`

Expected: compile failure because `AccountRole.ADMIN` does not exist.

- [ ] **Step 3: Write minimal implementation**

Change `AccountRole` to:

```java
@Getter
@AllArgsConstructor
public enum AccountRole {
    ADMIN("ADMIN"),
    MANAGER("MANAGER"),
    EMPLOYEE("EMPLOYEE");

    private final String value;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd "-Dtest=EntityMappingTest#accountRolesIncludeSystemAdminManagerAndEmployee" test`

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/entity/AccountRole.java backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java
git commit -m "feat: add admin account role"
```

### Task 2: Add Manager Account Creation Service

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/CreateEmployeeAccountRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`
- Create: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Create `ManagerAccountServiceTest` with tests for employee creation, duplicate username, and duplicate employee code:

```java
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
            passwordEncoder);

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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd "-Dtest=ManagerAccountServiceTest" test`

Expected: compile failure because `CreateEmployeeAccountRequest` and `ManagerAccountService` do not exist.

- [ ] **Step 3: Add request DTO**

Create `CreateEmployeeAccountRequest`:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmployeeAccountRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone) {
}
```

- [ ] **Step 4: Add service implementation**

Create `ManagerAccountService`:

```java
package scheduling.scheduling.auth;

import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class ManagerAccountService {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultEmployeePassword;

    public ManagerAccountService(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.default-employee-password}") String defaultEmployeePassword) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.defaultEmployeePassword = Objects.requireNonNull(defaultEmployeePassword, "defaultEmployeePassword");
    }

    @Transactional
    public AccountResponse createEmployeeAccount(CreateEmployeeAccountRequest request) {
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
                .passwordHash(passwordEncoder.encode(defaultEmployeePassword))
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.PENDING_ACTIVATION)
                .employee(savedEmployee)
                .build();

        return AccountResponse.from(accountRepository.save(account));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw.cmd "-Dtest=ManagerAccountServiceTest" test`

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/auth/dto/CreateEmployeeAccountRequest.java backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java
git commit -m "feat: add manager employee account service"
```

### Task 3: Add Manager Accounts Controller Endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountController.java`
- Create: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `ManagerAccountControllerTest`:

```java
package scheduling.scheduling.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.AccountRole;

@WebMvcTest(ManagerAccountController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class ManagerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerAccountService managerAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @Test
    void managerCreatesEmployeeAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager);
        when(managerAccountService.createEmployeeAccount(any(CreateEmployeeAccountRequest.class)))
                .thenReturn(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A",
                                  "email": "employee01@gmail.com",
                                  "phone": "0900000001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        verify(managerAccountService).createEmployeeAccount(any(CreateEmployeeAccountRequest.class));
    }

    @Test
    void employeeCannotCreateEmployeeAccount() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee02", AccountRole.EMPLOYEE);
        when(jwtService.parseAccessToken("employee-token")).thenReturn(employee);

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(managerAccountService);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd "-Dtest=ManagerAccountControllerTest" test`

Expected: compile failure because `ManagerAccountController` does not exist.

- [ ] **Step 3: Add controller implementation**

Create `ManagerAccountController`:

```java
package scheduling.scheduling.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;

@RestController
@RequestMapping("/api/manager/accounts")
public class ManagerAccountController {

    private final ManagerAccountService managerAccountService;

    public ManagerAccountController(ManagerAccountService managerAccountService) {
        this.managerAccountService = managerAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    ResponseEntity<AccountResponse> createEmployeeAccount(@Valid @RequestBody CreateEmployeeAccountRequest request) {
        return ResponseEntity.status(201).body(managerAccountService.createEmployeeAccount(request));
    }
}
```

- [ ] **Step 4: Enable method security**

Modify `SecurityConfig` class annotations:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
```

Add import:

```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw.cmd "-Dtest=ManagerAccountControllerTest" test`

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run full backend verification**

Run: `./mvnw.cmd clean test`

Expected: all tests pass.

- [ ] **Step 7: Commit and push**

```bash
git diff --check
git add backend/src/main/java/scheduling/scheduling/auth/ManagerAccountController.java backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java
git commit -m "feat: expose manager employee account endpoint"
git push
```

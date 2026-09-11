# Admin Account APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Phase 3 admin account listing and lock/unlock APIs with lifecycle-safe behavior and tests.

**Architecture:** Add a focused admin account module parallel to the existing admin employee module. Keep account lifecycle updates in `AdminAccountService`, expose them through `AdminAccountController`, and add repository filtering in `AccountRepository`.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Security method authorization, Spring Data JPA, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/admin/AdminAccountService.java`: list, lock, unlock account business logic.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminAccountController.java`: `/api/admin/accounts` endpoints.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountResponse.java`: account list response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountEmployeeResponse.java`: linked employee summary DTO.
- Modify `backend/src/main/java/scheduling/scheduling/repository/AccountRepository.java`: add optional-filter account search query.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminAccountServiceTest.java`: service tests.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminAccountControllerTest.java`: controller/security tests.

## Task 1: Account Response DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountEmployeeResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountResponse.java`

- [ ] **Step 1: Create linked employee response DTO**

Add `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountEmployeeResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Employee;

public record AdminAccountEmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String status) {

    public static AdminAccountEmployeeResponse from(Employee employee) {
        return new AdminAccountEmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getStatus());
    }
}
```

- [ ] **Step 2: Create account response DTO**

Add `backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;

public record AdminAccountResponse(
        Long id,
        String username,
        AccountRole role,
        AccountStatus status,
        Boolean active,
        AdminAccountEmployeeResponse employee) {

    public static AdminAccountResponse from(Account account) {
        Employee employee = account.getEmployee();
        return new AdminAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getStatus(),
                account.getIsActive(),
                employee == null ? null : AdminAccountEmployeeResponse.from(employee));
    }
}
```

- [ ] **Step 3: Compile check**

Run: `backend\\mvnw.cmd test -DskipTests`

Expected: compilation succeeds.

- [ ] **Step 4: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountEmployeeResponse.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminAccountResponse.java
```

## Task 2: Repository Account Search

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/AccountRepository.java`
- Test: covered by service tests in Task 3.

- [ ] **Step 1: Add imports and search query**

Replace `AccountRepository.java` with:

```java
package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("""
            select a
            from Account a
            left join a.employee e
            where (:query is null
                or lower(a.username) like lower(concat('%', :query, '%'))
                or lower(e.employeeCode) like lower(concat('%', :query, '%'))
                or lower(e.fullName) like lower(concat('%', :query, '%')))
              and (:role is null or a.role = :role)
              and (:status is null or a.status = :status)
            """)
    List<Account> search(
            @Param("query") String query,
            @Param("role") AccountRole role,
            @Param("status") AccountStatus status);
}
```

- [ ] **Step 2: Compile check**

Run: `backend\\mvnw.cmd test -DskipTests`

Expected: compilation succeeds.

- [ ] **Step 3: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/repository/AccountRepository.java
```

## Task 3: Admin Account Service

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminAccountService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminAccountServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminAccountServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run service tests and verify RED**

Run: `backend\\mvnw.cmd test "-Dtest=AdminAccountServiceTest"`

Expected: compilation fails because `AdminAccountService` does not exist.

- [ ] **Step 3: Implement service**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminAccountService.java`:

```java
package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.admin.dto.AdminAccountResponse;
import scheduling.scheduling.auth.AccountNotFoundException;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@Service
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminAccountService(AccountRepository accountRepository, RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAccounts(String query, String role, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        AccountRole parsedRole = StringUtils.hasText(role) ? AccountRole.valueOf(role.trim()) : null;
        AccountStatus parsedStatus = StringUtils.hasText(status) ? AccountStatus.valueOf(status.trim()) : null;
        return accountRepository.search(normalizedQuery, parsedRole, parsedStatus).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public void lockAccount(Long accountId) {
        Account account = findAccount(accountId);
        account.setIsActive(false);
        if (account.getStatus() == AccountStatus.ACTIVE) {
            account.setStatus(AccountStatus.INACTIVE);
        }
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(accountId);
    }

    @Transactional
    public void unlockAccount(Long accountId) {
        Account account = findAccount(accountId);
        account.setIsActive(true);
        if (account.getStatus() == AccountStatus.INACTIVE) {
            account.setStatus(AccountStatus.ACTIVE);
        }
        accountRepository.save(account);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
    }
}
```

- [ ] **Step 4: Run service tests and verify GREEN**

Run: `backend\\mvnw.cmd test "-Dtest=AdminAccountServiceTest"`

Expected: all `AdminAccountServiceTest` tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/admin/AdminAccountService.java backend/src/test/java/scheduling/scheduling/admin/AdminAccountServiceTest.java
```

## Task 4: Admin Account Controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminAccountController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminAccountControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminAccountControllerTest.java`:

```java
package scheduling.scheduling.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminAccountEmployeeResponse;
import scheduling.scheduling.admin.dto.AdminAccountResponse;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminAccountController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccountService adminAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsAccounts() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);
        when(adminAccountService.listAccounts("employee", "EMPLOYEE", "ACTIVE"))
                .thenReturn(List.of(accountResponse()));

        mockMvc.perform(get("/api/admin/accounts")
                        .param("query", "employee")
                        .param("role", "EMPLOYEE")
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].username").value("employee01"))
                .andExpect(jsonPath("$[0].employee.employeeCode").value("EMP001"));
    }

    @Test
    void managerLocksAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/admin/accounts/20/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminAccountService).lockAccount(20L);
    }

    @Test
    void managerUnlocksAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/admin/accounts/20/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminAccountService).unlockAccount(20L);
    }

    @Test
    void employeeCannotListAccounts() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(get("/api/admin/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminAccountService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static AdminAccountResponse accountResponse() {
        return new AdminAccountResponse(
                20L,
                "employee01",
                AccountRole.EMPLOYEE,
                AccountStatus.ACTIVE,
                true,
                new AdminAccountEmployeeResponse(10L, "EMP001", "Nguyen Van A", "employee01@example.com", "ACTIVE"));
    }
}
```

- [ ] **Step 2: Run controller tests and verify RED**

Run: `backend\\mvnw.cmd test "-Dtest=AdminAccountControllerTest"`

Expected: compilation fails because `AdminAccountController` does not exist.

- [ ] **Step 3: Implement controller**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminAccountController.java`:

```java
package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminAccountResponse;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<List<AdminAccountResponse>> listAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminAccountService.listAccounts(query, role, status));
    }

    @PostMapping("/{accountId}/lock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> lockAccount(@PathVariable Long accountId) {
        adminAccountService.lockAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/unlock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> unlockAccount(@PathVariable Long accountId) {
        adminAccountService.unlockAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run controller tests and verify GREEN**

Run: `backend\\mvnw.cmd test "-Dtest=AdminAccountControllerTest"`

Expected: all `AdminAccountControllerTest` tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/admin/AdminAccountController.java backend/src/test/java/scheduling/scheduling/admin/AdminAccountControllerTest.java
```

## Task 5: Documentation and Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update Phase 3 progress**

In `docs/project_plan.md`, mark these Phase 3 items complete:

```markdown
- [x] API danh sach tai khoan
- [x] API khoa account nhan vien
- [x] API mo khoa account nhan vien
- [x] Test admin account creation APIs
- [x] Test admin password reset/reactivation APIs
```

Keep unrelated Phase 3 items unchecked.

- [ ] **Step 2: Run full backend test suite**

Run: `backend\\mvnw.cmd clean test`

Expected: build success with all tests passing.

- [ ] **Step 3: Commit docs**

Run:

```powershell
git add -- docs/project_plan.md
```

- [ ] **Step 4: Final status check**

Run: `git status --short --branch`

Expected: clean branch ahead of `origin/main` by the new commits.

## Self-Review

- Spec coverage: list/search/filter, lock, unlock, lifecycle-safe status handling, refresh token revocation, role protection, and tests are covered by Tasks 1-5.
- Placeholder scan: no unfinished placeholder markers remain.
- Type consistency: DTO names, service method names, controller routes, enum names, and repository method names match across tasks.

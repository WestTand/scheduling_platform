# Phase 9C Manager Employees Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the manager employees page to backend employee/account APIs in backend mode while preserving mock mode.

**Architecture:** Add missing backend employee creation and refactor manager account creation to attach accounts to existing employees. Then add frontend backend-aware employee/account services and keep `EmployeesPage` mostly unchanged, with soft-delete mapped to lock and backend-mode messages that do not mention mock OTP.

**Tech Stack:** Spring Boot 4, Spring Security, JUnit/MockMvc, Mockito, React, TypeScript, Vite.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateEmployeeRequest.java`: DTO for `POST /api/admin/employees`.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeController.java`: add create employee endpoint.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`: implement create employee and reuse existing soft-delete lock semantics.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeControllerTest.java`: controller tests for create employee.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`: service tests for create employee and duplicate employee code.
- Modify `backend/src/main/java/scheduling/scheduling/auth/dto/CreateEmployeeAccountRequest.java`: change account creation request to `employeeId` + `username`.
- Modify `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`: create account for existing employee instead of creating a new employee.
- Modify `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java`: controller request shape tests.
- Modify `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`: service tests for existing employee account creation.
- Create `frontend/src/types/backendEmployee.ts`: backend DTO types for admin employee/account summaries.
- Create `frontend/src/adapters/employeeAdapter.ts`: map backend DTOs to frontend `Employee` and `UserAccount`.
- Create `frontend/src/services/backendEmployeeService.ts`: admin employee API client.
- Create `frontend/src/services/backendManagerAccountService.ts`: manager account API client.
- Modify `frontend/src/services/employeeService.ts`: mock/backend-aware service methods.
- Modify `frontend/src/services/accountService.ts`: backend-aware manager account methods while preserving mock activation methods.
- Modify `frontend/src/pages/manager/EmployeesPage.tsx`: account derivation and backend-mode messages.

### Task 1: Backend Create Employee Endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateEmployeeRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`

- [ ] **Step 1: Add failing controller test**

Add this import to `AdminEmployeeControllerTest.java`:

```java
import scheduling.scheduling.admin.dto.CreateEmployeeRequest;
```

Add this test after `managerListsEmployees`:

```java
    @Test
    void managerCreatesEmployee() throws Exception {
        CurrentAccountPrincipal manager = manager();
        givenToken("manager-token", manager);
        when(adminEmployeeService.createEmployee(any(CurrentAccountPrincipal.class), any(CreateEmployeeRequest.class)))
                .thenReturn(employeeResponse());

        mockMvc.perform(post("/api/admin/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A",
                                  "email": "employee01@example.com",
                                  "phone": "0900000001",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.employeeCode").value("EMP001"));

        verify(adminEmployeeService).createEmployee(any(CurrentAccountPrincipal.class), any(CreateEmployeeRequest.class));
    }
```

- [ ] **Step 2: Add failing service tests**

Add this import to `AdminEmployeeServiceTest.java`:

```java
import scheduling.scheduling.admin.dto.CreateEmployeeRequest;
import scheduling.scheduling.auth.DuplicateResourceException;
```

Add these tests after `listEmployeesSearchesCodeOrNameAndFiltersStatus`:

```java
    @Test
    void createEmployeeSavesEmployeeAndRecordsActivityLog() {
        Account manager = givenManager();
        CreateEmployeeRequest request = new CreateEmployeeRequest(
                "EMP003",
                "Le Van C",
                "employee03@example.com",
                "0900000003",
                "ACTIVE");
        Employee saved = employee(12L, "EMP003", "Le Van C", "ACTIVE");
        saved.setEmail("employee03@example.com");
        saved.setPhone("0900000003");
        when(employeeRepository.existsByEmployeeCode("EMP003")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenReturn(saved);

        AdminEmployeeResponse response = service.createEmployee(managerPrincipal(), request);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.employeeCode()).isEqualTo("EMP003");
        verify(employeeRepository).save(argThat(employee ->
                "EMP003".equals(employee.getEmployeeCode())
                        && "Le Van C".equals(employee.getFullName())
                        && "employee03@example.com".equals(employee.getEmail())
                        && "0900000003".equals(employee.getPhone())
                        && "ACTIVE".equals(employee.getStatus())));
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
                "EMP003",
                "Le Van C",
                "employee03@example.com",
                "0900000003",
                "ACTIVE");
        when(employeeRepository.existsByEmployeeCode("EMP003")).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(managerPrincipal(), request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Employee code already exists");

        verify(employeeRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminEmployeeControllerTest,AdminEmployeeServiceTest test
```

Expected: compilation fails because `CreateEmployeeRequest` and `createEmployee` do not exist.

- [ ] **Step 4: Create request DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateEmployeeRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
}
```

- [ ] **Step 5: Add controller endpoint**

Add imports to `AdminEmployeeController.java`:

```java
import org.springframework.http.HttpStatus;
import scheduling.scheduling.admin.dto.CreateEmployeeRequest;
```

Add this method before `@GetMapping`:

```java
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminEmployeeResponse> createEmployee(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminEmployeeService.createEmployee(principal, request));
    }
```

- [ ] **Step 6: Implement service method**

Add imports to `AdminEmployeeService.java`:

```java
import scheduling.scheduling.admin.dto.CreateEmployeeRequest;
import scheduling.scheduling.auth.DuplicateResourceException;
```

Add this method before `listEmployees`:

```java
    @Transactional
    public AdminEmployeeResponse createEmployee(CurrentAccountPrincipal principal, CreateEmployeeRequest request) {
        Account manager = requireManager(principal);
        String employeeCode = request.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(employeeCode)) {
            throw new DuplicateResourceException("Employee code already exists");
        }

        Employee employee = Employee.builder()
                .employeeCode(employeeCode)
                .fullName(request.fullName().trim())
                .email(request.email() == null ? null : request.email().trim())
                .phone(request.phone() == null ? null : request.phone().trim())
                .status(request.status())
                .build();
        Employee saved = employeeRepository.save(employee);
        activityLogService.record(manager, "CREATE", "employees", saved.getId(), null, employeeSnapshot(saved));
        return AdminEmployeeResponse.from(saved);
    }
```

- [ ] **Step 7: Run tests to verify pass**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminEmployeeControllerTest,AdminEmployeeServiceTest test
```

Expected: tests pass.

- [ ] **Step 8: Commit**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/dto/CreateEmployeeRequest.java backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeController.java backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeControllerTest.java backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java
git commit -m "feat: add admin employee creation"
```

### Task 2: Account Creation For Existing Employee

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/dto/CreateEmployeeAccountRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java`
- Modify: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`

- [ ] **Step 1: Update controller test request shape**

In `ManagerAccountControllerTest.managerCreatesEmployeeAccount`, replace JSON body with:

```java
                        .content("""
                                {
                                  "employeeId": 10,
                                  "username": "employee01"
                                }
                                """))
```

The existing verification using `any(CreateEmployeeAccountRequest.class)` stays.

- [ ] **Step 2: Replace service tests for create account**

In `ManagerAccountServiceTest`, replace `createEmployeeAccountUsesDefaultPasswordAndPendingActivationStatus` with:

```java
    @Test
    void createEmployeeAccountUsesExistingEmployeeDefaultPasswordAndPendingActivationStatus() {
        CreateEmployeeAccountRequest request = request();
        Employee employee = employee();
        employee.setId(10L);
        Account savedAccount = Account.builder()
                .username("employee01")
                .passwordHash("encoded-1111")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.PENDING_ACTIVATION)
                .isActive(true)
                .employee(employee)
                .build();
        savedAccount.setId(20L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(accountRepository.existsByUsername("employee01")).thenReturn(false);
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-1111");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = service.createEmployeeAccount(managerPrincipal(), request);

        verify(employeeRepository, never()).save(any(Employee.class));
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUsername()).isEqualTo("employee01");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("encoded-1111");
        assertThat(accountCaptor.getValue().getRole()).isEqualTo(AccountRole.EMPLOYEE);
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(accountCaptor.getValue().getIsActive()).isTrue();
        assertThat(accountCaptor.getValue().getEmployee()).isSameAs(employee);
        verify(otpService).createActivationOtp(savedAccount);
        assertThat(response).isEqualTo(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));
    }
```

Replace `createEmployeeAccountRecordsEmployeeCreateActivityLog` with:

```java
    @Test
    void createEmployeeAccountRecordsAccountCreateActivityLog() {
        Employee employee = employee();
        employee.setId(10L);
        Account savedAccount = Account.builder()
                .username("employee01")
                .passwordHash("encoded-1111")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.PENDING_ACTIVATION)
                .isActive(true)
                .employee(employee)
                .build();
        savedAccount.setId(20L);
        Account manager = managerAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(passwordEncoder.encode("configured-default")).thenReturn("encoded-1111");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        service.createEmployeeAccount(managerPrincipal(), request());

        verify(activityLogService).record(
                same(manager),
                eq("CREATE"),
                eq("accounts"),
                eq(20L),
                isNull(),
                argThat((Object snapshot) -> snapshot instanceof Map<?, ?> map
                        && Long.valueOf(20L).equals(map.get("id"))
                        && "employee01".equals(map.get("username"))
                        && Long.valueOf(10L).equals(map.get("employeeId"))
                        && "PENDING_ACTIVATION".equals(map.get("status"))));
    }
```

Replace `createEmployeeAccountRejectsDuplicateEmployeeCode` with:

```java
    @Test
    void createEmployeeAccountRejectsEmployeeWithExistingAccount() {
        Employee employee = employee();
        employee.setId(10L);
        employee.setAccount(account(21L, AccountStatus.ACTIVE, AccountRole.EMPLOYEE, true));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.createEmployeeAccount(managerPrincipal(), request()))
                .isInstanceOf(InvalidAccountStateException.class)
                .hasMessage("Invalid account state");

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(otpService);
    }
```

Update helper `request()` to:

```java
    private static CreateEmployeeAccountRequest request() {
        return new CreateEmployeeAccountRequest(10L, "employee01");
    }
```

- [ ] **Step 3: Add missing employee test**

Add this test near duplicate username test:

```java
    @Test
    void createEmployeeAccountRejectsMissingEmployee() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        when(employeeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEmployeeAccount(managerPrincipal(), request()))
                .isInstanceOf(scheduling.scheduling.admin.EmployeeNotFoundException.class)
                .hasMessage("Employee not found");

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(otpService);
    }
```

- [ ] **Step 4: Run tests to verify failure**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=ManagerAccountControllerTest,ManagerAccountServiceTest test
```

Expected: compilation fails because `CreateEmployeeAccountRequest` still has the old shape.

- [ ] **Step 5: Change request DTO**

Replace `CreateEmployeeAccountRequest.java` with:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeAccountRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 50) String username) {
}
```

- [ ] **Step 6: Refactor service implementation**

In `ManagerAccountService.java`, add import:

```java
import scheduling.scheduling.admin.EmployeeNotFoundException;
```

Replace `createEmployeeAccount` with:

```java
    @Transactional
    public AccountResponse createEmployeeAccount(CurrentAccountPrincipal principal, CreateEmployeeAccountRequest request) {
        Account manager = requireManager(principal);
        String username = request.username().trim();
        if (accountRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists");
        }

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(EmployeeNotFoundException::new);
        if (employee.getAccount() != null) {
            throw new InvalidAccountStateException();
        }

        Account account = Account.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(defaultEmployeePassword))
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.PENDING_ACTIVATION)
                .employee(employee)
                .build();

        Account savedAccount = accountRepository.save(account);
        otpService.createActivationOtp(savedAccount);
        activityLogService.record(manager, "CREATE", "accounts", savedAccount.getId(), null, Map.of(
                "id", savedAccount.getId(),
                "username", savedAccount.getUsername(),
                "employeeId", employee.getId(),
                "status", savedAccount.getStatus().name()));

        return AccountResponse.from(savedAccount);
    }
```

- [ ] **Step 7: Run tests to verify pass**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=ManagerAccountControllerTest,ManagerAccountServiceTest test
```

Expected: tests pass.

- [ ] **Step 8: Commit**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/auth/dto/CreateEmployeeAccountRequest.java backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java
git commit -m "feat: create accounts for existing employees"
```

### Task 3: Frontend Backend DTOs And Adapters

**Files:**
- Create: `frontend/src/types/backendEmployee.ts`
- Create: `frontend/src/adapters/employeeAdapter.ts`
- Modify: `frontend/src/types/account.ts`

- [ ] **Step 1: Create backend DTO types**

Create `frontend/src/types/backendEmployee.ts`:

```ts
import type { AccountStatus } from '@/types/account'

export interface BackendAdminEmployeeAccountResponse {
  id: number
  username: string
  role: 'MANAGER' | 'EMPLOYEE' | 'ADMIN'
  status: AccountStatus | 'FORCE_PASSWORD_CHANGE' | 'INACTIVE'
  active: boolean
}

export interface BackendAdminEmployeeResponse {
  id: number
  employeeCode: string
  fullName: string
  email?: string | null
  phone?: string | null
  status: 'ACTIVE' | 'INACTIVE'
  account?: BackendAdminEmployeeAccountResponse | null
}
```

- [ ] **Step 2: Widen frontend account status for backend values**

Modify `frontend/src/types/account.ts` `AccountStatus` object:

```ts
export const AccountStatus = {
  PENDING_ACTIVATION: 'PENDING_ACTIVATION',
  FORCE_PASSWORD_CHANGE: 'FORCE_PASSWORD_CHANGE',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  LOCKED: 'LOCKED',
  DISABLED: 'DISABLED',
} as const
```

- [ ] **Step 3: Create adapters**

Create `frontend/src/adapters/employeeAdapter.ts`:

```ts
import type { BackendAdminEmployeeResponse } from '@/types/backendEmployee'
import type { UserAccount, AccountStatus } from '@/types/account'
import type { Employee } from '@/types/management'

export const adaptBackendEmployee = (employee: BackendAdminEmployeeResponse): Employee => ({
  id: String(employee.id),
  employeeCode: employee.employeeCode,
  fullName: employee.fullName,
  email: employee.email ?? '',
  phone: employee.phone ?? '',
  role: employee.account?.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
  status: employee.status,
  joinDate: new Date().toISOString().slice(0, 10),
  note: undefined,
})

export const adaptBackendAccount = (employee: BackendAdminEmployeeResponse): UserAccount | null => {
  if (!employee.account) {
    return null
  }

  return {
    id: String(employee.account.id),
    employeeId: String(employee.id),
    username: employee.account.username,
    email: employee.email ?? '',
    role: employee.account.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
    status: employee.account.status as AccountStatus,
    createdAt: new Date().toISOString(),
    activatedAt: null,
    lastLoginAt: null,
  }
}
```

- [ ] **Step 4: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 5: Commit**

Run:

```powershell
git add frontend/src/types/backendEmployee.ts frontend/src/adapters/employeeAdapter.ts frontend/src/types/account.ts
git commit -m "feat: add backend employee adapters"
```

### Task 4: Frontend Backend Employee Service

**Files:**
- Create: `frontend/src/services/backendEmployeeService.ts`
- Modify: `frontend/src/services/employeeService.ts`

- [ ] **Step 1: Create backend employee service**

Create `frontend/src/services/backendEmployeeService.ts`:

```ts
import { adaptBackendAccount, adaptBackendEmployee } from '@/adapters/employeeAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { UserAccount } from '@/types/account'
import type { BackendAdminEmployeeResponse } from '@/types/backendEmployee'
import type { Employee, EmployeeFormValues, PaginatedResult } from '@/types/management'

const pageResult = <T>(items: T[], page: number, pageSize: number): PaginatedResult<T> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const employeePayload = (data: EmployeeFormValues) => ({
  employeeCode: data.employeeCode.trim(),
  fullName: data.fullName.trim(),
  email: data.email.trim(),
  phone: data.phone.trim(),
  status: data.status,
})

export const backendEmployeeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string }) {
    const params = new URLSearchParams()
    if (search.trim()) params.set('query', search.trim())
    if (status !== 'ALL') params.set('status', status)
    const query = params.toString()
    const response = await requestJson<BackendAdminEmployeeResponse[]>(`/api/admin/employees${query ? `?${query}` : ''}`)
    return pageResult(response.map(adaptBackendEmployee), page, pageSize)
  },

  async listAccounts(): Promise<UserAccount[]> {
    const response = await requestJson<BackendAdminEmployeeResponse[]>('/api/admin/employees')
    return response.map(adaptBackendAccount).filter((account): account is UserAccount => account !== null)
  },

  async getById(id: string): Promise<Employee | null> {
    const employee = await requestJson<BackendAdminEmployeeResponse>(`/api/admin/employees/${id}`)
    return adaptBackendEmployee(employee)
  },

  async add(data: EmployeeFormValues): Promise<Employee> {
    const employee = await requestJson<BackendAdminEmployeeResponse>('/api/admin/employees', {
      method: 'POST',
      body: employeePayload(data),
    })
    return adaptBackendEmployee(employee)
  },

  async update(id: string, data: EmployeeFormValues): Promise<Employee> {
    const employee = await requestJson<BackendAdminEmployeeResponse>(`/api/admin/employees/${id}`, {
      method: 'PUT',
      body: {
        fullName: data.fullName.trim(),
        email: data.email.trim(),
        phone: data.phone.trim(),
        status: data.status,
      },
    })
    return adaptBackendEmployee(employee)
  },

  async lock(id: string): Promise<void> {
    await requestVoid(`/api/admin/employees/${id}/lock`, { method: 'POST' })
  },

  async unlock(id: string): Promise<void> {
    await requestVoid(`/api/admin/employees/${id}/unlock`, { method: 'POST' })
  },
}
```

- [ ] **Step 2: Make employeeService backend-aware**

At the top of `frontend/src/services/employeeService.ts`, add:

```ts
import { apiConfig } from '@/config/api'
import { backendEmployeeService } from '@/services/backendEmployeeService'
```

In each exported method, add backend branch before mock delay:

```ts
if (!apiConfig.useMockApi) {
  return backendEmployeeService.list({ page, pageSize, search, status })
}
```

Use these exact backend branches:

```ts
// getById
if (!apiConfig.useMockApi) {
  return backendEmployeeService.getById(id)
}

// add
if (!apiConfig.useMockApi) {
  return backendEmployeeService.add(data)
}

// update
if (!apiConfig.useMockApi) {
  return backendEmployeeService.update(id, data)
}

// toggleStatus
if (!apiConfig.useMockApi) {
  const employee = await backendEmployeeService.getById(id)
  if (!employee) throw new Error('Nhân viên không tồn tại.')
  if (employee.status === 'ACTIVE') {
    await backendEmployeeService.lock(id)
  } else {
    await backendEmployeeService.unlock(id)
  }
  return backendEmployeeService.getById(id)
}

// remove
if (!apiConfig.useMockApi) {
  const employee = await backendEmployeeService.getById(id)
  if (!employee) throw new Error('Nhân viên không tồn tại.')
  await backendEmployeeService.lock(id)
  return { ...employee, status: 'INACTIVE' }
}
```

Leave `reset()` mock-only; in backend mode it should not call the API.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/src/services/backendEmployeeService.ts frontend/src/services/employeeService.ts
git commit -m "feat: wire employee service to backend"
```

### Task 5: Frontend Backend Manager Account Service

**Files:**
- Create: `frontend/src/services/backendManagerAccountService.ts`
- Modify: `frontend/src/services/accountService.ts`

- [ ] **Step 1: Create backend manager account service**

Create `frontend/src/services/backendManagerAccountService.ts`:

```ts
import { requestJson, requestVoid } from '@/services/apiClient'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import type { UserAccount, CreateEmployeeAccountInput } from '@/types/account'
import type { BackendAuthResponse } from '@/types/backendAuth'

export const backendManagerAccountService = {
  async getAccounts(): Promise<UserAccount[]> {
    return backendEmployeeService.listAccounts()
  },

  async getAccountByEmployeeId(employeeId: string): Promise<UserAccount | null> {
    const accounts = await backendEmployeeService.listAccounts()
    return accounts.find((account) => account.employeeId === employeeId) ?? null
  },

  async getAccountById(accountId: string): Promise<UserAccount | null> {
    const accounts = await backendEmployeeService.listAccounts()
    return accounts.find((account) => account.id === accountId) ?? null
  },

  async createEmployeeAccount(input: CreateEmployeeAccountInput): Promise<UserAccount> {
    await requestJson<BackendAuthResponse['account']>('/api/manager/accounts', {
      method: 'POST',
      body: {
        employeeId: Number(input.employeeId),
        username: input.username.trim(),
      },
    })
    const account = await this.getAccountByEmployeeId(input.employeeId)
    if (!account) {
      throw new Error('Tài khoản đã được tạo nhưng chưa tải lại được dữ liệu.')
    }
    return account
  },

  async resendActivationOtp(accountId: string): Promise<void> {
    await requestVoid(`/api/manager/accounts/${accountId}/activation/resend`, { method: 'POST' })
  },

  async resetPassword(accountId: string): Promise<void> {
    await requestVoid(`/api/manager/accounts/${accountId}/password/reset`, { method: 'POST' })
  },
}
```

- [ ] **Step 2: Make accountService manager methods backend-aware**

At the top of `frontend/src/services/accountService.ts`, add:

```ts
import { apiConfig } from '@/config/api'
import { backendManagerAccountService } from '@/services/backendManagerAccountService'
```

Add these backend branches at the top of the matching methods:

```ts
// getAccounts
if (!apiConfig.useMockApi) {
  return backendManagerAccountService.getAccounts()
}

// getAccountByEmployeeId
if (!apiConfig.useMockApi) {
  return backendManagerAccountService.getAccountByEmployeeId(employeeId)
}

// getAccountById
if (!apiConfig.useMockApi) {
  return backendManagerAccountService.getAccountById(accountId)
}

// createEmployeeAccount
if (!apiConfig.useMockApi) {
  return backendManagerAccountService.createEmployeeAccount(input)
}

// resendActivationOtp
if (!apiConfig.useMockApi) {
  await backendManagerAccountService.resendActivationOtp(accountId)
  const account = await backendManagerAccountService.getAccountById(accountId)
  return { account: account as UserAccount, maskedEmail: account?.email ?? '' }
}
```

Do not route auth activation methods like `requestActivation`, `verifyActivationOtp`, or `setNewPassword` through this service; those are already handled by `authService` in backend mode.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/src/services/backendManagerAccountService.ts frontend/src/services/accountService.ts
git commit -m "feat: wire manager account service to backend"
```

### Task 6: Employees Page Backend-Mode UI Adjustments

**Files:**
- Modify: `frontend/src/pages/manager/EmployeesPage.tsx`

- [ ] **Step 1: Update resend OTP message**

In `handleResendOtp`, replace:

```ts
window.alert('Mã OTP mới đã được gửi tới email nhân viên. OTP mock đang sử dụng: 123456.')
```

with:

```ts
window.alert(apiConfig.useMockApi ? 'Mã OTP mới đã được gửi tới email nhân viên. OTP mock đang sử dụng: 123456.' : 'Mã OTP mới đã được gửi tới email nhân viên.')
```

Add import if missing:

```ts
import { apiConfig } from '@/config/api'
```

- [ ] **Step 2: Update delete confirmation wording**

Find the `ConfirmDialog` for `deleteTarget`. Change title/description so backend soft-delete is clear:

```tsx
<ConfirmDialog
  open={Boolean(deleteTarget)}
  title="Tạm ngưng nhân viên"
  description={`Nhân viên ${deleteTarget?.fullName ?? ''} sẽ được chuyển sang trạng thái tạm ngưng. Dữ liệu nhân viên và tài khoản sẽ không bị xóa khỏi hệ thống.`}
  confirmLabel="Tạm ngưng"
  onConfirm={handleDelete}
  onCancel={() => setDeleteTarget(null)}
/>
```

Keep the icon button if the UI still uses `Trash2`; this task changes semantics text, not visual design.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/src/pages/manager/EmployeesPage.tsx
git commit -m "fix: clarify backend employee soft delete"
```

### Task 7: Backend Integration Verification

**Files:**
- No source files expected unless verification exposes a bug.

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminEmployeeControllerTest,AdminEmployeeServiceTest,ManagerAccountControllerTest,ManagerAccountServiceTest test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run auth lifecycle regression tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest,AuthServiceTest test
```

Expected: all selected tests pass.

- [ ] **Step 3: If activity log JSON rollback is suspected, run local smoke test**

If Docker backend is running with the latest code and a manager token can be obtained, run a manual smoke through frontend or API:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/admin/employees' -Headers @{ Authorization = 'Bearer <manager-access-token>' } -ContentType 'application/json' -Body '{"employeeCode":"EMP-SMOKE-01","fullName":"Smoke Employee","email":"smoke.employee@example.com","phone":"0900000001","status":"ACTIVE"}'
```

Then create account:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/manager/accounts' -Headers @{ Authorization = 'Bearer <manager-access-token>' } -ContentType 'application/json' -Body '{"employeeId":<created-employee-id>,"username":"smokeemployee01"}'
```

Expected: both requests succeed. If the second request sends email and then rolls back with `lower(bytea)`, fix the activity log binding bug before proceeding.

- [ ] **Step 4: Commit only bug fixes**

If verification required a source fix, commit only the fixed files with a specific message. If no fix was needed, do not create a commit.

### Task 8: Frontend Verification

**Files:**
- No source files expected unless verification exposes a bug.

- [ ] **Step 1: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 2: Run backend-mode frontend build**

Run:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 3: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. Line-ending warnings alone are acceptable.

- [ ] **Step 4: Manual browser smoke**

With Docker dev stack running in backend mode:

- Login as manager.
- Open `/manager/employees`.
- Add a new employee.
- Create an account for that employee.
- Confirm the account shows `PENDING_ACTIVATION`.
- Resend OTP and confirm success message does not mention mock OTP.
- Soft-delete the employee and confirm status changes to inactive.
- Unlock the employee and confirm status changes to active.

- [ ] **Step 5: Commit only bug fixes**

If verification required a source fix, commit only the fixed files with a specific message. If no fix was needed, do not create a commit.

## Self-Review Notes

- Spec coverage: create employee, account for existing employee, soft-delete, backend-aware services, backend-mode OTP text, and verification are covered.
- Placeholder scan: no unresolved `TBD`, `TODO`, or deferred implementation markers are present.
- Type consistency: backend request fields are `employeeId` and `username`; frontend `CreateEmployeeAccountInput` remains compatible and only sends those fields in backend mode.

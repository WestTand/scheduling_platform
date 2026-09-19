# Employee Swap Requests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Phase 4 employee swap-request workflow (create, list related, cancel, target respond, manager list/approve/reject) plus the `/api/auth/me` profile test, following TDD.

**Architecture:** Mirror the existing leave-request pattern exactly: `SwapRequestRepository` with fetch-join detail queries, `EmployeeSwapRequestService` + controller under `employee/`, `AdminSwapRequestService` + controller under `admin/`, one shared `SwapRequestResponse` record. Status transitions only; no assignment mutation.

**Tech Stack:** Java 21, Spring Boot (WebMvc, Data JPA, Validation, Security), JUnit 5, AssertJ, Mockito, MockMvc `@WebMvcTest`, Maven wrapper `backend/mvnw.cmd`.

---

## File Structure

New files and their responsibility:

- `backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java` — fetch-join queries: `findDetailById`, `findRelatedForEmployee`, `findAllDetail`.
- `backend/src/main/java/scheduling/scheduling/employee/dto/CreateSwapRequestRequest.java` — create body record with Bean Validation.
- `backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java` — respond body record (`Boolean accept`, `@NotNull`).
- `backend/src/main/java/scheduling/scheduling/employee/dto/SwapRequestResponse.java` — shared response record with nested `EmployeeSummary`, `from(SwapRequest)` mapper.
- `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java` — employee rules: ownership, PUBLISHED period, target checks, cancel/respond.
- `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestController.java` — `POST/GET /api/employee/swap-requests`, `POST .../cancel`, `POST .../respond`.
- `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java` — manager list/approve/reject with `Clock`-based `reviewedAt`.
- `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestController.java` — `GET/POST /api/admin/swap-requests...`.
- `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java` — Mockito unit tests.
- `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestControllerTest.java` — `@WebMvcTest` route/validation/role tests.
- `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java` — Mockito unit tests with fixed `Clock`.
- `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestControllerTest.java` — `@WebMvcTest` tests.

Modified files:

- `backend/src/main/java/scheduling/scheduling/entity/SwapRequest.java` — JavaDoc comment only: add `CANCELLED` to status flow.
- `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java` — add `/me` employee-profile test.
- `docs/project_plan.md` — tick 6 Phase 4 items (profile, 2 swap APIs, 3 test flows).

---

## Conventions (read before implementing)

- Errors: `ResponseStatusException` with `HttpStatus` + reason string. 404 reasons: `"Assignment not found"`, `"Employee not found"`, `"Swap request not found"`. 400 reasons: `"Employee is not active"`, `"Cannot target yourself"`, `"Assignment is not in a published schedule"`, `"Target assignment does not belong to target employee"`, `"Target assignment must differ from requester assignment"`, `"Swap request is not pending target response"`, `"Swap request is not pending approval"`, `"Swap request is not reviewable"`, `"Invalid swap request"`, `"Invalid swap review request"`.
- Employee lookup: `employeeAccess.requireActiveEmployee(principal)` for reads, `requireActiveEmployeeForUpdate(principal)` for writes.
- Manager lookup: `accountRepository.findById(principal.accountId())`, role must be `MANAGER`, else `403 "Manager access required"`.
- Status normalization: trim, exact match against the 5 statuses, else 400.
- Reason trimming: `StringUtils.hasText(...) ? ....trim() : null`.
- Maven from repo root (`C:\Users\Admin\Desktop\NhanSu\scheduling_platform`): `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=ClassName"`. Full suite: `.\backend\mvnw.cmd -f backend/pom.xml test`.
- After every implementation step run `git diff --check` (LF/CRLF warnings are pre-existing and OK).

---

### Task 1: SwapRequestRepository

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java`

- [ ] **Step 1: Create the repository interface**

```java
package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.SwapRequest;

public interface SwapRequestRepository extends JpaRepository<SwapRequest, Long> {

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where sr.id = :id
            """)
    Optional<SwapRequest> findDetailById(@Param("id") Long id);

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where (ra.employee.id = :employeeId or te.id = :employeeId)
              and (:status is null or sr.status = :status)
            order by sr.id desc
            """)
    List<SwapRequest> findRelatedForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status);

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where (:status is null or sr.status = :status)
            order by sr.id desc
            """)
    List<SwapRequest> findAllDetail(@Param("status") String status);
}
```

- [ ] **Step 2: Verify it compiles (no unit test for a query interface; coverage comes from service tests)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q compile`
Expected: BUILD SUCCESS, no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java
git commit -m "feat: add swap request repository queries"
```

---

### Task 2: Swap DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/CreateSwapRequestRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/SwapRequestResponse.java`

- [ ] **Step 1: Create the three records**

`CreateSwapRequestRequest.java`:
```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSwapRequestRequest(
        @NotNull Long requesterAssignmentId,
        @NotNull Long targetEmployeeId,
        Long targetAssignmentId,
        @Size(max = 1000) String reason) {
}
```

`RespondSwapRequestRequest.java`:
```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;

public record RespondSwapRequestRequest(@NotNull Boolean accept) {
}
```

`SwapRequestResponse.java`:
```java
package scheduling.scheduling.employee.dto;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;

public record SwapRequestResponse(
        Long id,
        Long requesterAssignmentId,
        EmployeeSummary requesterEmployee,
        Long targetAssignmentId,
        EmployeeSummary targetEmployee,
        String reason,
        String status,
        Long reviewedByAccountId,
        String reviewedByUsername,
        LocalDateTime reviewedAt) {

    public record EmployeeSummary(Long id, String employeeCode, String fullName) {
    }

    public static SwapRequestResponse from(SwapRequest swapRequest) {
        ShiftAssignment requesterAssignment = swapRequest.getRequesterAssignment();
        Employee requester = requesterAssignment.getEmployee();
        Employee target = swapRequest.getTargetEmployee();
        Account reviewedBy = swapRequest.getReviewedBy();
        return new SwapRequestResponse(
                swapRequest.getId(),
                requesterAssignment.getId(),
                new EmployeeSummary(requester.getId(), requester.getEmployeeCode(), requester.getFullName()),
                swapRequest.getTargetAssignment() == null ? null : swapRequest.getTargetAssignment().getId(),
                new EmployeeSummary(target.getId(), target.getEmployeeCode(), target.getFullName()),
                swapRequest.getReason(),
                swapRequest.getStatus(),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getUsername(),
                swapRequest.getReviewedAt());
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/dto/CreateSwapRequestRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/SwapRequestResponse.java
git commit -m "feat: add swap request DTOs"
```

---

### Task 3: EmployeeSwapRequestService create flow (TDD)

**Files:**
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java`

Shared test helpers used by every test in this file (write once in Step 1):

```java
private static final long ACCOUNT_ID = 2L;

private static CurrentAccountPrincipal employeePrincipal() {
    return new CurrentAccountPrincipal(ACCOUNT_ID, "employee01", AccountRole.EMPLOYEE);
}

private static Employee employee(Long id, String status) {
    Employee employee = new Employee();
    employee.setId(id);
    employee.setEmployeeCode("NV" + id);
    employee.setFullName("Nhan vien " + id);
    employee.setStatus(status);
    return employee;
}

private static Account account(CurrentAccountPrincipal principal, Employee employee) {
    Account account = Account.builder()
            .username(principal.username())
            .passwordHash("hash")
            .role(principal.role())
            .status(AccountStatus.ACTIVE)
            .isActive(true)
            .employee(employee)
            .build();
    account.setId(principal.accountId());
    return account;
}

private static ShiftAssignment assignment(Long id, Employee employee, String periodStatus) {
    SchedulePeriod period = new SchedulePeriod();
    period.setStatus(periodStatus);
    WorkShift workShift = new WorkShift();
    workShift.setSchedulePeriod(period);
    ShiftAssignment assignment = new ShiftAssignment();
    assignment.setId(id);
    assignment.setEmployee(employee);
    assignment.setWorkShift(workShift);
    return assignment;
}
```

File shell (package, imports, class, mock fields — write once in Step 1):

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

class EmployeeSwapRequestServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final SwapRequestRepository swapRequestRepository = mock(SwapRequestRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeeSwapRequestService service = new EmployeeSwapRequestService(
            employeeAccess, swapRequestRepository, shiftAssignmentRepository, employeeRepository);

    // tests + helpers go here
}
```

- [ ] **Step 1: Write failing tests for create (happy path + 6 error cases)**

Happy path test:

```java
@Test
void createSwapRequestDefaultsPendingTargetAndTrimsReason() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, requester, "PUBLISHED");
    CreateSwapRequestRequest request = new CreateSwapRequestRequest(100L, 9L, null, "  Cover me  ");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(requesterAssignment));
    when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> {
        SwapRequest saved = invocation.getArgument(0);
        saved.setId(50L);
        return saved;
    });

    SwapRequestResponse response = service.createSwapRequest(principal, request);

    assertThat(response.id()).isEqualTo(50L);
    assertThat(response.requesterAssignmentId()).isEqualTo(100L);
    assertThat(response.targetAssignmentId()).isNull();
    assertThat(response.requesterEmployee().employeeCode()).isEqualTo("NV7");
    assertThat(response.targetEmployee().employeeCode()).isEqualTo("NV9");
    assertThat(response.reason()).isEqualTo("Cover me");
    assertThat(response.status()).isEqualTo("PENDING_TARGET");
}
```

Error tests (each asserts `ResponseStatusException` with exact status code, and `verify(swapRequestRepository, never()).save(any())`):

```java
@Test
void createSwapRequestRejectsAssignmentOwnedByAnotherEmployee() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    ShiftAssignment otherAssignment = assignment(100L, employee(8L, "ACTIVE"), "PUBLISHED");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(otherAssignment));

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsMissingAssignment() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsAssignmentOutsidePublishedPeriod() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(assignment(100L, requester, "DRAFT")));
    when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "ACTIVE")));

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsInactiveTargetEmployee() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
    when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "INACTIVE")));

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsTargetingSelf() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
    when(employeeRepository.findById(7L)).thenReturn(Optional.of(requester));

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 7L, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsTargetAssignmentOwnedByAnotherEmployee() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));
    when(shiftAssignmentRepository.findDetailById(100L))
            .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
    when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
    when(shiftAssignmentRepository.findDetailById(200L))
            .thenReturn(Optional.of(assignment(200L, employee(11L, "ACTIVE"), "PUBLISHED")));

    assertThatThrownBy(() -> service.createSwapRequest(
            principal, new CreateSwapRequestRequest(100L, 9L, 200L, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void createSwapRequestRejectsNullRequest() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee requester = employee(7L, "ACTIVE");
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, requester)));

    assertThatThrownBy(() -> service.createSwapRequest(principal, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}
```

- [ ] **Step 2: Run tests, verify RED (compilation error: EmployeeSwapRequestService does not exist)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: FAIL — `cannot find symbol: class EmployeeSwapRequestService` (feature missing, not a typo in the test).

- [ ] **Step 3: Implement the service with create + list/cancel/respond stubs that throw, plus full create logic**

Create `EmployeeSwapRequestService.java` with the complete `createSwapRequest` implementation below. Include `listSwapRequests`, `cancelSwapRequest`, `respondSwapRequest` as stubs throwing `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, ...)` — Task 4 replaces them (tests for those methods do not exist yet, so no green test turns red).

```java
package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class EmployeeSwapRequestService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String PENDING_TARGET = "PENDING_TARGET";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final EmployeeAccess employeeAccess;
    private final SwapRequestRepository swapRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeSwapRequestService(
            EmployeeAccess employeeAccess,
            SwapRequestRepository swapRequestRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            EmployeeRepository employeeRepository) {
        this.employeeAccess = employeeAccess;
        this.swapRequestRepository = swapRequestRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public SwapRequestResponse createSwapRequest(
            CurrentAccountPrincipal principal, CreateSwapRequestRequest request) {
        if (request == null) {
            throw invalidRequest();
        }
        Employee requester = employeeAccess.requireActiveEmployeeForUpdate(principal);
        ShiftAssignment requesterAssignment = shiftAssignmentRepository.findDetailById(request.requesterAssignmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!requester.getId().equals(requesterAssignment.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        requirePublished(requesterAssignment);
        Employee target = employeeRepository.findById(request.targetEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee is not active");
        }
        if (requester.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot target yourself");
        }
        ShiftAssignment targetAssignment = null;
        if (request.targetAssignmentId() != null) {
            targetAssignment = shiftAssignmentRepository.findDetailById(request.targetAssignmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
            if (!target.getId().equals(targetAssignment.getEmployee().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Target assignment does not belong to target employee");
            }
            if (requesterAssignment.getId().equals(targetAssignment.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Target assignment must differ from requester assignment");
            }
            requirePublished(targetAssignment);
        }
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(target)
                .targetAssignment(targetAssignment)
                .reason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null)
                .status(PENDING_TARGET)
                .build();
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> listSwapRequests(CurrentAccountPrincipal principal, String status) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Transactional
    public SwapRequestResponse cancelSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Transactional
    public SwapRequestResponse respondSwapRequest(
            CurrentAccountPrincipal principal, Long swapRequestId, RespondSwapRequestRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    private static void requirePublished(ShiftAssignment assignment) {
        if (!PUBLISHED.equals(assignment.getWorkShift().getSchedulePeriod().getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Assignment is not in a published schedule");
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid swap request");
    }
}
```

- [ ] **Step 4: Run tests, verify GREEN**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: 8 tests PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java
git commit -m "feat: add employee swap request creation"
```

---

### Task 4: EmployeeSwapRequestService list/cancel/respond (TDD)

**Files:**
- Modify: `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java` (append tests)
- Modify: `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java` (replace the 3 stubs)

- [ ] **Step 1: Append failing tests for list/cancel/respond**

```java
@Test
void listSwapRequestsReturnsRelatedRequestsNewestFirst() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(7L, "ACTIVE");
    SwapRequest mine = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
    SwapRequest invited = swapRequest(51L, assignment(200L, employee(9L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findRelatedForEmployee(7L, "PENDING_TARGET"))
            .thenReturn(List.of(mine, invited));

    List<SwapRequestResponse> response = service.listSwapRequests(principal, " PENDING_TARGET ");

    assertThat(response).extracting(SwapRequestResponse::id).containsExactly(50L, 51L);
    verify(swapRequestRepository).findRelatedForEmployee(7L, "PENDING_TARGET");
}

@Test
void listSwapRequestsRejectsInvalidStatus() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(7L, "ACTIVE");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));

    assertThatThrownBy(() -> service.listSwapRequests(principal, "WAITING"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}

@Test
void cancelSwapRequestMarksPendingTargetCancelled() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(7L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.cancelSwapRequest(principal, 50L);

    assertThat(response.status()).isEqualTo("CANCELLED");
}

@Test
void cancelSwapRequestRejectsNonRequester() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(7L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(
            50L, assignment(100L, employee(8L, "ACTIVE"), "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.cancelSwapRequest(principal, 50L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void cancelSwapRequestRejectsNonPendingTarget() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(7L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_APPROVAL");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.cancelSwapRequest(principal, 50L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void respondSwapRequestAcceptMovesToPendingApproval() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(9L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(
            50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.respondSwapRequest(
            principal, 50L, new RespondSwapRequestRequest(true));

    assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
}

@Test
void respondSwapRequestDeclineMarksRejected() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(9L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(
            50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.respondSwapRequest(
            principal, 50L, new RespondSwapRequestRequest(false));

    assertThat(response.status()).isEqualTo("REJECTED");
}

@Test
void respondSwapRequestRejectsNonTarget() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(11L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(
            50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.respondSwapRequest(
            principal, 50L, new RespondSwapRequestRequest(true)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void respondSwapRequestRejectsNullBody() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee me = employee(9L, "ACTIVE");
    SwapRequest swapRequest = swapRequest(
            50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
    when(accountRepository.findById(ACCOUNT_ID))
            .thenReturn(Optional.of(account(principal, me)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.respondSwapRequest(principal, 50L, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}
```

Helper used above (add next to the other helpers):

```java
private static SwapRequest swapRequest(
        Long id, ShiftAssignment requesterAssignment, Employee target, String status) {
    SwapRequest swapRequest = SwapRequest.builder()
            .requesterAssignment(requesterAssignment)
            .targetEmployee(target)
            .status(status)
            .build();
    swapRequest.setId(id);
    return swapRequest;
}
```

- [ ] **Step 2: Run tests, verify RED**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: FAIL — 9 new tests fail with `501 NOT_IMPLEMENTED` (stub behavior, proving the stubs are hit).

- [ ] **Step 3: Replace the three stubs with real implementations**

```java
@Transactional(readOnly = true)
public List<SwapRequestResponse> listSwapRequests(CurrentAccountPrincipal principal, String status) {
    Employee employee = employeeAccess.requireActiveEmployee(principal);
    String normalizedStatus = normalizeStatus(status);
    return swapRequestRepository.findRelatedForEmployee(employee.getId(), normalizedStatus).stream()
            .map(SwapRequestResponse::from)
            .toList();
}

@Transactional
public SwapRequestResponse cancelSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
    Employee employee = employeeAccess.requireActiveEmployee(principal);
    SwapRequest swapRequest = findVisibleToRequester(swapRequestId, employee.getId());
    if (!PENDING_TARGET.equals(swapRequest.getStatus())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Swap request is not pending target response");
    }
    swapRequest.setStatus(CANCELLED);
    return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
}

@Transactional
public SwapRequestResponse respondSwapRequest(
        CurrentAccountPrincipal principal, Long swapRequestId, RespondSwapRequestRequest request) {
    if (request == null) {
        throw invalidRequest();
    }
    Employee employee = employeeAccess.requireActiveEmployee(principal);
    SwapRequest swapRequest = swapRequestRepository.findDetailById(swapRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found"));
    if (!employee.getId().equals(swapRequest.getTargetEmployee().getId())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found");
    }
    if (!PENDING_TARGET.equals(swapRequest.getStatus())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Swap request is not pending target response");
    }
    swapRequest.setStatus(Boolean.TRUE.equals(request.accept()) ? PENDING_APPROVAL : REJECTED);
    return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
}

private SwapRequest findVisibleToRequester(Long swapRequestId, Long employeeId) {
    SwapRequest swapRequest = swapRequestRepository.findDetailById(swapRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found"));
    if (!employeeId.equals(swapRequest.getRequesterAssignment().getEmployee().getId())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found");
    }
    return swapRequest;
}

private static String normalizeStatus(String status) {
    if (!StringUtils.hasText(status)) {
        return null;
    }
    String normalized = status.trim();
    if (!PENDING_TARGET.equals(normalized) && !PENDING_APPROVAL.equals(normalized)
            && !APPROVED.equals(normalized) && !REJECTED.equals(normalized)
            && !CANCELLED.equals(normalized)) {
        throw invalidRequest();
    }
    return normalized;
}
```

Note: `invalidRequest()` and status constants already exist from Task 3.

- [ ] **Step 4: Run tests, verify GREEN**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: 17 tests PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java
git commit -m "feat: add employee swap list cancel respond"
```

---

### Task 5: EmployeeSwapRequestController (TDD)

**Files:**
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestControllerTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestController.java`

- [ ] **Step 1: Write failing controller tests**

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeSwapRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeSwapRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeSwapRequestService employeeSwapRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsRelatedSwapRequests() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.listSwapRequests(any(CurrentAccountPrincipal.class), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/swap-requests")
                        .param("status", "PENDING_TARGET")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("PENDING_TARGET"))
                .andExpect(jsonPath("$[0].targetEmployee.employeeCode").value("NV9"));
    }

    @Test
    void employeeCreatesSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.createSwapRequest(
                any(CurrentAccountPrincipal.class), any(CreateSwapRequestRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requesterAssignmentId": 100,
                                  "targetEmployeeId": 9,
                                  "reason": "Cover me"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50));

        ArgumentCaptor<CreateSwapRequestRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateSwapRequestRequest.class);
        verify(employeeSwapRequestService).createSwapRequest(
                any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().requesterAssignmentId()).isEqualTo(100L);
    }

    @Test
    void createSwapRequestRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEmployeeId": 9
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeSwapRequestService);
    }

    @Test
    void employeeCancelsOwnSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.cancelSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(cancelledResponse());

        mockMvc.perform(post("/api/employee/swap-requests/50/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(employeeSwapRequestService).cancelSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void targetRespondsToSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.respondSwapRequest(
                any(CurrentAccountPrincipal.class), eq(50L), any(RespondSwapRequestRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/swap-requests/50/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accept": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_TARGET"));

        ArgumentCaptor<RespondSwapRequestRequest> requestCaptor =
                ArgumentCaptor.forClass(RespondSwapRequestRequest.class);
        verify(employeeSwapRequestService).respondSwapRequest(
                any(CurrentAccountPrincipal.class), eq(50L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().accept()).isTrue();
    }

    @Test
    void respondSwapRequestRejectsMissingAccept() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/swap-requests/50/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeSwapRequestService);
    }

    @Test
    void managerCannotAccessEmployeeSwapApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeSwapRequestService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
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

    private static SwapRequestResponse response() {
        return new SwapRequestResponse(
                50L,
                100L,
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me",
                "PENDING_TARGET",
                null,
                null,
                null);
    }

    private static SwapRequestResponse cancelledResponse() {
        return new SwapRequestResponse(
                50L,
                100L,
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me",
                "CANCELLED",
                null,
                null,
                null);
    }
}
```

- [ ] **Step 2: Run tests, verify RED (compilation error: EmployeeSwapRequestController does not exist)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestControllerTest"`
Expected: FAIL — `cannot find symbol: class EmployeeSwapRequestController`.

- [ ] **Step 3: Implement the controller**

```java
package scheduling.scheduling.employee;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;

@RestController
@RequestMapping("/api/employee/swap-requests")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeSwapRequestController {

    private final EmployeeSwapRequestService employeeSwapRequestService;

    public EmployeeSwapRequestController(EmployeeSwapRequestService employeeSwapRequestService) {
        this.employeeSwapRequestService = employeeSwapRequestService;
    }

    @GetMapping
    ResponseEntity<List<SwapRequestResponse>> listSwapRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(employeeSwapRequestService.listSwapRequests(principal, status));
    }

    @PostMapping
    ResponseEntity<SwapRequestResponse> createSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreateSwapRequestRequest request) {
        return ResponseEntity.status(201).body(employeeSwapRequestService.createSwapRequest(principal, request));
    }

    @PostMapping("/{swapRequestId}/cancel")
    ResponseEntity<SwapRequestResponse> cancelSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(employeeSwapRequestService.cancelSwapRequest(principal, swapRequestId));
    }

    @PostMapping("/{swapRequestId}/respond")
    ResponseEntity<SwapRequestResponse> respondSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId,
            @Valid @RequestBody RespondSwapRequestRequest request) {
        return ResponseEntity.ok(employeeSwapRequestService.respondSwapRequest(principal, swapRequestId, request));
    }
}
```

- [ ] **Step 4: Run tests, verify GREEN**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=EmployeeSwapRequestControllerTest"`
Expected: 7 tests PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestControllerTest.java backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestController.java
git commit -m "feat: add employee swap request controller"
```

---

### Task 6: AdminSwapRequestService (TDD)

**Files:**
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java`

- [ ] **Step 1: Write the test file shell + failing tests for approve/reject/list**

File shell (write once):

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

class AdminSwapRequestServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneId.of("UTC"));

    private final SwapRequestRepository swapRequestRepository = mock(SwapRequestRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminSwapRequestService service =
            new AdminSwapRequestService(swapRequestRepository, accountRepository, FIXED_CLOCK);

    // tests + helpers go here
}
```

Helpers (same style as Task 3, plus manager fixtures):

```java
private static CurrentAccountPrincipal managerPrincipal() {
    return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
}

private static Account managerAccount(CurrentAccountPrincipal principal) {
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

private static Employee employee(Long id, String status) {
    Employee employee = new Employee();
    employee.setId(id);
    employee.setEmployeeCode("NV" + id);
    employee.setFullName("Nhan vien " + id);
    employee.setStatus(status);
    return employee;
}

private static ShiftAssignment assignment(Long id, Employee employee, String periodStatus) {
    SchedulePeriod period = new SchedulePeriod();
    period.setStatus(periodStatus);
    WorkShift workShift = new WorkShift();
    workShift.setSchedulePeriod(period);
    ShiftAssignment assignment = new ShiftAssignment();
    assignment.setId(id);
    assignment.setEmployee(employee);
    assignment.setWorkShift(workShift);
    return assignment;
}

private static SwapRequest pendingApprovalRequest(
        Long id, Employee requester, Employee target, String status) {
    SwapRequest swapRequest = SwapRequest.builder()
            .requesterAssignment(assignment(100L, requester, "PUBLISHED"))
            .targetEmployee(target)
            .status(status)
            .build();
    swapRequest.setId(id);
    return swapRequest;
}
```

Tests:

```java
@Test
void managerListsAllSwapRequests() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    SwapRequest swapRequest = pendingApprovalRequest(50L, requester, target, "PENDING_APPROVAL");
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findAllDetail("PENDING_APPROVAL"))
            .thenReturn(List.of(swapRequest));

    List<SwapRequestResponse> response = service.listSwapRequests(principal, "PENDING_APPROVAL");

    assertThat(response).extracting(SwapRequestResponse::id).containsExactly(50L);
    verify(swapRequestRepository).findAllDetail("PENDING_APPROVAL");
}

@Test
void managerListRejectsInvalidStatus() {
    CurrentAccountPrincipal principal = managerPrincipal();
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));

    assertThatThrownBy(() -> service.listSwapRequests(principal, "WAITING"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}

@Test
void nonManagerCannotListSwapRequests() {
    CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    Employee me = employee(7L, "ACTIVE");
    Account employeeAccount = Account.builder()
            .username("employee01")
            .passwordHash("hash")
            .role(AccountRole.EMPLOYEE)
            .status(AccountStatus.ACTIVE)
            .isActive(true)
            .employee(me)
            .build();
    employeeAccount.setId(2L);
    when(accountRepository.findById(2L)).thenReturn(Optional.of(employeeAccount));

    assertThatThrownBy(() -> service.listSwapRequests(principal, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
}

@Test
void approvePendingApprovalMarksApprovedWithReviewMetadata() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    SwapRequest swapRequest = pendingApprovalRequest(50L, requester, target, "PENDING_APPROVAL");
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.approveSwapRequest(principal, 50L);

    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(response.reviewedByAccountId()).isEqualTo(1L);
    assertThat(response.reviewedByUsername()).isEqualTo("manager01");
    assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 8, 0));
}

@Test
void approveSwapRequestRejectsNonPendingApproval() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    SwapRequest swapRequest = pendingApprovalRequest(50L, requester, target, "PENDING_TARGET");
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.approveSwapRequest(principal, 50L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void rejectPendingTargetMarksRejectedWithReviewMetadata() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    SwapRequest swapRequest = pendingApprovalRequest(50L, requester, target, "PENDING_TARGET");
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.rejectSwapRequest(principal, 50L);

    assertThat(response.status()).isEqualTo("REJECTED");
    assertThat(response.reviewedByAccountId()).isEqualTo(1L);
    assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 8, 0));
}

@Test
void rejectSwapRequestRejectsTerminalStatus() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(7L, "ACTIVE");
    Employee target = employee(9L, "ACTIVE");
    SwapRequest swapRequest = pendingApprovalRequest(50L, requester, target, "APPROVED");
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailById(50L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.rejectSwapRequest(principal, 50L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any());
}

@Test
void reviewSwapRequestReturnsNotFoundForMissingRequest() {
    CurrentAccountPrincipal principal = managerPrincipal();
    when(accountRepository.findById(1L))
            .thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.approveSwapRequest(principal, 404L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

    verify(swapRequestRepository, never()).save(any());
}
```

- [ ] **Step 2: Run tests, verify RED (compilation error: AdminSwapRequestService does not exist)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=AdminSwapRequestServiceTest"`
Expected: FAIL — `cannot find symbol: class AdminSwapRequestService`.

- [ ] **Step 3: Implement the admin service**

```java
package scheduling.scheduling.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class AdminSwapRequestService {

    private static final String PENDING_TARGET = "PENDING_TARGET";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final SwapRequestRepository swapRequestRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    @Autowired
    public AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository, AccountRepository accountRepository) {
        this(swapRequestRepository, accountRepository, Clock.systemDefaultZone());
    }

    AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository, AccountRepository accountRepository, Clock clock) {
        this.swapRequestRepository = swapRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> listSwapRequests(CurrentAccountPrincipal principal, String status) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        return swapRequestRepository.findAllDetail(normalizedStatus).stream()
                .map(SwapRequestResponse::from)
                .toList();
    }

    @Transactional
    public SwapRequestResponse approveSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        Account manager = requireManager(principal);
        SwapRequest swapRequest = findDetail(swapRequestId);
        if (!PENDING_APPROVAL.equals(swapRequest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Swap request is not pending approval");
        }
        applyReview(swapRequest, manager, APPROVED);
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    @Transactional
    public SwapRequestResponse rejectSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        Account manager = requireManager(principal);
        SwapRequest swapRequest = findDetail(swapRequestId);
        if (!PENDING_TARGET.equals(swapRequest.getStatus())
                && !PENDING_APPROVAL.equals(swapRequest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Swap request is not reviewable");
        }
        applyReview(swapRequest, manager, REJECTED);
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private SwapRequest findDetail(Long swapRequestId) {
        return swapRequestRepository.findDetailById(swapRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found"));
    }

    private void applyReview(SwapRequest swapRequest, Account manager, String status) {
        swapRequest.setStatus(status);
        swapRequest.setReviewedBy(manager);
        swapRequest.setReviewedAt(LocalDateTime.now(clock));
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING_TARGET.equals(normalized) && !PENDING_APPROVAL.equals(normalized)
                && !APPROVED.equals(normalized) && !REJECTED.equals(normalized)
                && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid swap review request");
    }
}
```

- [ ] **Step 4: Run tests, verify GREEN**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=AdminSwapRequestServiceTest"`
Expected: 8 tests PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java
git commit -m "feat: add manager swap request review service"
```

---

### Task 7: AdminSwapRequestController (TDD)

**Files:**
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestControllerTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestController.java`

- [ ] **Step 1: Write failing controller tests**

```java
package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminSwapRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSwapRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSwapRequestService adminSwapRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsSwapRequests() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.listSwapRequests(any(CurrentAccountPrincipal.class), any()))
                .thenReturn(List.of(response("PENDING_APPROVAL")));

        mockMvc.perform(get("/api/admin/swap-requests")
                        .param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void managerApprovesSwapRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.approveSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/swap-requests/50/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(adminSwapRequestService).approveSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void managerRejectsSwapRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.rejectSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/api/admin/swap-requests/50/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(adminSwapRequestService).rejectSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void employeeCannotAccessAdminSwapApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSwapRequestService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
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

    private static SwapRequestResponse response(String status) {
        return new SwapRequestResponse(
                50L,
                100L,
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me",
                status,
                1L,
                "manager01",
                null);
    }
}
```

- [ ] **Step 2: Run tests, verify RED (compilation error: AdminSwapRequestController does not exist)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=AdminSwapRequestControllerTest"`
Expected: FAIL — `cannot find symbol: class AdminSwapRequestController`.

- [ ] **Step 3: Implement the controller**

```java
package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;

@RestController
@RequestMapping("/api/admin/swap-requests")
@PreAuthorize("hasRole('MANAGER')")
public class AdminSwapRequestController {

    private final AdminSwapRequestService adminSwapRequestService;

    public AdminSwapRequestController(AdminSwapRequestService adminSwapRequestService) {
        this.adminSwapRequestService = adminSwapRequestService;
    }

    @GetMapping
    ResponseEntity<List<SwapRequestResponse>> listSwapRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminSwapRequestService.listSwapRequests(principal, status));
    }

    @PostMapping("/{swapRequestId}/approve")
    ResponseEntity<SwapRequestResponse> approveSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(adminSwapRequestService.approveSwapRequest(principal, swapRequestId));
    }

    @PostMapping("/{swapRequestId}/reject")
    ResponseEntity<SwapRequestResponse> rejectSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(adminSwapRequestService.rejectSwapRequest(principal, swapRequestId));
    }
}
```

- [ ] **Step 4: Run tests, verify GREEN**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=AdminSwapRequestControllerTest"`
Expected: 4 tests PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestControllerTest.java backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestController.java
git commit -m "feat: add manager swap request controller"
```

---

### Task 8: SwapRequest JavaDoc + /me profile test (TDD)

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/SwapRequest.java` (comment only)
- Modify: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java` (append test)

- [ ] **Step 1: Update the entity JavaDoc status flow**

Old (in `SwapRequest.java` class comment):
```
 * Status flow:
 *   PENDING_TARGET → PENDING_APPROVAL → APPROVED / REJECTED
```
New:
```
 * Status flow:
 *   PENDING_TARGET → PENDING_APPROVAL → APPROVED / REJECTED
 *   PENDING_TARGET → CANCELLED (requester cancels before target responds)
 *   PENDING_TARGET / PENDING_APPROVAL → REJECTED (target declines or manager rejects)
```

- [ ] **Step 2: Add the failing /me profile test first**

Append to `AuthControllerTest.java` (add `import scheduling.scheduling.auth.dto.EmployeeResponse;` to imports):

```java
@Test
void meReturnsEmployeeProfileForEmployee() throws Exception {
    CurrentAccountPrincipal principal = new CurrentAccountPrincipal(42L, "employee01", AccountRole.EMPLOYEE);
    when(jwtService.parseAccessToken("valid-access-token")).thenReturn(principal);
    when(accountRepository.findById(42L)).thenReturn(Optional.of(account(42L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true)));
    when(authService.me(principal)).thenReturn(new MeResponse(
            new AccountResponse(42L, "employee01", AccountRole.EMPLOYEE),
            new EmployeeResponse(7L, "NV007", "Nguyen Van A", "a@example.com", "0900000007", "ACTIVE")));

    mockMvc.perform(get("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id").value(42))
            .andExpect(jsonPath("$.employee.id").value(7))
            .andExpect(jsonPath("$.employee.employeeCode").value("NV007"))
            .andExpect(jsonPath("$.employee.fullName").value("Nguyen Van A"))
            .andExpect(jsonPath("$.employee.email").value("a@example.com"))
            .andExpect(jsonPath("$.employee.phone").value("0900000007"))
            .andExpect(jsonPath("$.employee.status").value("ACTIVE"));
    verify(authService).me(principal);
}
```

Note: the `account(...)` helper with this 5-arg signature already exists in `AuthControllerTest` (used by the existing `/me` tests); reuse it, do not add a duplicate.

- [ ] **Step 3: Run the auth tests, verify GREEN (production `/me` code already exists; this test documents the profile contract)**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test "-Dtest=AuthControllerTest"`
Expected: all tests PASS including the new one. If the new test fails, fix the test expectation first (the endpoint is existing behavior; do not change `AuthService.me`).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/entity/SwapRequest.java backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java
git commit -m "test: cover employee profile via me endpoint"
```

---

### Task 9: Full suite + project_plan ticks

**Files:**
- Modify: `docs/project_plan.md` (6 checkbox lines in Phase 4, lines 330/357/360/363/366/369)

- [ ] **Step 1: Run the full backend suite**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test`
Expected: BUILD SUCCESS, 0 failures (baseline was 423 tests; now 423 + ~40 new).

- [ ] **Step 2: Tick the Phase 4 items in `docs/project_plan.md`**

Apply these 6 exact edits (keep the 2-line descriptions untouched):

1. `- [ ] API nhan vien xem profile ca nhan` → `- [x] API nhan vien xem profile ca nhan (da xong: dung GET /api/auth/me)`
2. `- [ ] API gui yeu cau doi ca` → `- [x] API gui yeu cau doi ca (da xong: POST /api/employee/swap-requests + target respond + manager approve/reject, status-only)`
3. `- [ ] API xem yeu cau doi ca lien quan den minh` → `- [x] API xem yeu cau doi ca lien quan den minh (da xong: GET /api/employee/swap-requests + GET /api/admin/swap-requests)`
4. `- [ ] Test leave request flow` → `- [x] Test leave request flow (da xong: EmployeeLeaveRequest*Test pass trong full suite)`
5. `- [ ] Test preference flow` → `- [x] Test preference flow (da xong: EmployeePreference*Test pass trong full suite)`
6. `- [ ] Test swap request flow` → `- [x] Test swap request flow (da xong: EmployeeSwapRequest*Test + AdminSwapRequest*Test)`

- [ ] **Step 3: Verify diff check is clean**

Run: `git diff --check`
Expected: no output (LF/CRLF warnings pre-existing are OK).

- [ ] **Step 4: Commit**

```bash
git add docs/project_plan.md
git commit -m "docs: tick phase 4 swap profile items"
```

---

## Self-Review

**1. Spec coverage:** every spec section maps to a task — profile reuse + test (Task 8), create/list/cancel/respond (Tasks 3–5), admin list/approve/reject (Tasks 6–7), PUBLISHED validation (Task 3 tests + impl), `Boolean @NotNull` accept (Tasks 3–5 DTO + controller missing-accept test), `left join fetch` nullable target (Task 1), `order by id desc` (Task 1), `LocalDateTime.now(clock)` (Task 6 fixed-clock test), response fields (Task 2), duplicate = non-goal (no code), JavaDoc update (Task 8), plan ticks (Task 9).

**2. Placeholder scan:** no TODO/TBD/vague steps; every code step shows exact code, exact commands, exact expected output.

**3. Type consistency:** service/controller/repository method names match across test and impl tasks (`createSwapRequest`, `listSwapRequests`, `cancelSwapRequest`, `respondSwapRequest`, `approveSwapRequest`, `rejectSwapRequest`, `findDetailById`, `findRelatedForEmployee`, `findAllDetail`); `SwapRequestResponse` constructor arity (10 args) matches in all tasks; `EmployeeSummary` nested record construction matches.

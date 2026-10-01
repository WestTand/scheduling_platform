# Swap Request Target Shift Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the swap request a real two-way trade: the targeted employee must pick one of their own shifts when confirming, and manager approval must actually swap the two `ShiftAssignment` employees in the database.

**Architecture:** Extend `RespondSwapRequestRequest` with an optional `targetAssignmentId`; when `accept=true` it becomes mandatory and is validated (owned by the responder, published period, differs from requester assignment). On manager approve, swap `employee` between the two assignments and mark both `assignmentStatus = "SWAPPED"`. Frontend adds a shift-picker dialog in the respond flow, replacing the single click-through confirm button.

**Tech Stack:** Java 17 / Spring Boot / JPA, React 19 + TypeScript + Vite, Vitest, JUnit 5 + AssertJ + Mockito.

---

## Important design constraints

- `swap_requests.target_assignment_id` is nullable in the DB and existing rows may have `NULL`. New flow only *requires* it when a target accepts; historical rows stay readable.
- `shift_assignments` has unique constraint `(work_shift_id, employee_id)`. Swapping two employees between two shifts cannot collide **unless** both employees already hold a row in each other's shift. That case must be rejected explicitly before any write.
- Both assignments must belong to `PUBLISHED` periods at respond time and at approve time.
- Existing `AdminSwapRequestService` constructor has 2 forms (public autowired 5-arg, package-private 4-arg with `Clock`). Add the new dependency to both.

---

### Task 1: Backend - target shift is mandatory on accept

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add to `EmployeeSwapRequestServiceTest`. Use the existing mock fixture style in that file (mocks for `EmployeeAccess`, `SwapRequestRepository`, `ShiftAssignmentRepository`, `EmployeeRepository`, `NotificationService`).

```java
@Test
void respondSwapRequestStoresTargetAssignmentWhenAccepting() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    Employee requester = employee(10L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, requester, "PUBLISHED");
    ShiftAssignment targetAssignment = assignment(200L, target, "PUBLISHED");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.findDetailById(200L)).thenReturn(Optional.of(targetAssignment));
    when(swapRequestRepository.save(any(SwapRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(true, 200L));

    assertThat(response.targetAssignmentId()).isEqualTo(200L);
    assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
    assertThat(swapRequest.getTargetAssignment()).isSameAs(targetAssignment);
}
```

```java
@Test
void respondSwapRequestRejectsAcceptWithoutTargetAssignment() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, employee(10L, "ACTIVE"), "PUBLISHED");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(true, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Vui lòng chọn ca của bạn để đổi")
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(swapRequestRepository, never()).save(any(SwapRequest.class));
}
```

```java
@Test
void respondSwapRequestRejectsTargetAssignmentNotOwnedByResponder() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, employee(10L, "ACTIVE"), "PUBLISHED");
    ShiftAssignment someoneElsesShift = assignment(300L, employee(30L, "ACTIVE"), "PUBLISHED");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.findDetailById(300L)).thenReturn(Optional.of(someoneElsesShift));

    assertThatThrownBy(() -> service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(true, 300L)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Ca được chọn không thuộc về bạn")
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

```java
@Test
void respondSwapRequestRejectsRequesterOwnShiftAsTarget() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, employee(10L, "ACTIVE"), "PUBLISHED");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.findDetailById(100L)).thenReturn(Optional.of(requesterAssignment));

    assertThatThrownBy(() -> service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(true, 100L)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Ca được chọn không thuộc về bạn")
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

```java
@Test
void respondSwapRequestRejectsTargetShiftFromUnpublishedPeriod() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, employee(10L, "ACTIVE"), "PUBLISHED");
    ShiftAssignment draftShift = assignment(200L, target, "DRAFT");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.findDetailById(200L)).thenReturn(Optional.of(draftShift));

    assertThatThrownBy(() -> service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(true, 200L)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

```java
@Test
void respondSwapRequestAllowsRejectWithoutTargetAssignment() {
    CurrentAccountPrincipal principal = employeePrincipal();
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = assignment(100L, employee(10L, "ACTIVE"), "PUBLISHED");
    SwapRequest swapRequest = pendingSwapRequest(requesterAssignment, target, null);
    when(employeeAccess.requireActiveEmployee(principal)).thenReturn(target);
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(swapRequestRepository.save(any(SwapRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    SwapRequestResponse response = service.respondSwapRequest(
            principal, 5L, new RespondSwapRequestRequest(false, null));

    assertThat(response.status()).isEqualTo("REJECTED");
}
```

Helper methods (add to the test class, matching existing fixtures):

```java
private static SwapRequest pendingSwapRequest(
        ShiftAssignment requesterAssignment, Employee target, ShiftAssignment targetAssignment) {
    return SwapRequest.builder()
            .requesterAssignment(requesterAssignment)
            .targetEmployee(target)
            .targetAssignment(targetAssignment)
            .status("PENDING_TARGET")
            .build();
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

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: FAIL - compilation error, `RespondSwapRequestRequest` has no 2-arg constructor.

- [ ] **Step 3: Extend the request DTO**

Replace `backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java` with:

```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;

public record RespondSwapRequestRequest(@NotNull Boolean accept, Long targetAssignmentId) {
}
```

- [ ] **Step 4: Validate and store the target assignment**

In `EmployeeSwapRequestService.respondSwapRequest`, replace the block from `boolean accepted = ...` through `swapRequest.setStatus(...)` with:

```java
        boolean accepted = Boolean.TRUE.equals(request.accept());
        if (accepted) {
            requireSelectableTargetAssignment(request.targetAssignmentId(), swapRequest, employee);
            swapRequest.setTargetAssignment(loadSelectableTargetAssignment(request.targetAssignmentId(), employee));
        }
        swapRequest.setStatus(accepted ? PENDING_APPROVAL : REJECTED);
```

Add these private methods:

```java
    private ShiftAssignment loadSelectableTargetAssignment(
            Long targetAssignmentId, Employee employee, ShiftAssignment requesterAssignment) {
        ShiftAssignment targetAssignment = shiftAssignmentRepository.findDetailById(targetAssignmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ca được chọn không tồn tại"));
        if (!employee.getId().equals(targetAssignment.getEmployee().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ca được chọn không thuộc về bạn.");
        }
        if (requesterAssignment != null && requesterAssignment.getId().equals(targetAssignment.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ca được chọn không thuộc về bạn.");
        }
        return targetAssignment;
    }

    private void requireSelectableTargetAssignment(
            Long targetAssignmentId, SwapRequest swapRequest, Employee employee) {
        if (targetAssignmentId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ca của bạn để đổi trước khi xác nhận.");
        }
        ShiftAssignment targetAssignment = loadSelectableTargetAssignment(
                targetAssignmentId, employee, swapRequest.getRequesterAssignment());
        requirePublished(targetAssignment);
    }
```

And the call site becomes:

```java
        boolean accepted = Boolean.TRUE.equals(request.accept());
        if (accepted) {
            requireSelectableTargetAssignment(request.targetAssignmentId(), swapRequest, employee);
            swapRequest.setTargetAssignment(loadSelectableTargetAssignment(
                    request.targetAssignmentId(), employee, swapRequest.getRequesterAssignment()));
        }
        swapRequest.setStatus(accepted ? PENDING_APPROVAL : REJECTED);
```

`loadSelectableTargetAssignment` is called twice on the accept path: the first call validates and the second re-reads the assignment to attach it. To avoid a duplicate query, have `requireSelectableTargetAssignment` return the validated assignment and use that single value:

```java
        boolean accepted = Boolean.TRUE.equals(request.accept());
        if (accepted) {
            swapRequest.setTargetAssignment(
                    requireSelectableTargetAssignment(request.targetAssignmentId(), swapRequest, employee));
        }
        swapRequest.setStatus(accepted ? PENDING_APPROVAL : REJECTED);
```

with `requireSelectableTargetAssignment` returning `ShiftAssignment`.

Update the existing test in the same file that called the 1-arg constructor: for a reject test use `new RespondSwapRequestRequest(false, null)`; for an accept test supply a valid target assignment id and stub `shiftAssignmentRepository.findDetailById` for it.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=EmployeeSwapRequestServiceTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/dto/RespondSwapRequestRequest.java backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java
git commit -m "feat: require target shift when employee accepts a swap"
```

---

### Task 2: Backend - approve actually swaps the two assignments

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void approveSwapRequestExchangesTheTwoAssignments() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(10L, "ACTIVE");
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
    ShiftAssignment targetAssignment = publishedAssignment(200L, target);
    SwapRequest swapRequest = pendingApprovalSwapRequest(requesterAssignment, target, targetAssignment);
    when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(swapRequestRepository.save(any(SwapRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    service.approveSwapRequest(principal, 5L);

    assertThat(requesterAssignment.getEmployee()).isSameAs(target);
    assertThat(targetAssignment.getEmployee()).isSameAs(requester);
    assertThat(requesterAssignment.getAssignmentStatus()).isEqualTo("SWAPPED");
    assertThat(targetAssignment.getAssignmentStatus()).isEqualTo("SWAPPED");
    verify(shiftAssignmentRepository).saveAll(List.of(requesterAssignment, targetAssignment));
}
```

```java
@Test
void approveSwapRequestRejectsMissingTargetAssignment() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(10L, "ACTIVE");
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
    SwapRequest swapRequest = pendingApprovalSwapRequest(requesterAssignment, target, null);
    when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Yêu cầu đổi ca chưa có ca đối ứng")
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

    verify(shiftAssignmentRepository, never()).saveAll(anyList());
}
```

```java
@Test
void approveSwapRequestRejectsAssignmentsNotInPublishedPeriod() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(10L, "ACTIVE");
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
    ShiftAssignment draftTargetAssignment = draftAssignment(200L, target);
    SwapRequest swapRequest = pendingApprovalSwapRequest(requesterAssignment, target, draftTargetAssignment);
    when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));

    assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

    verify(shiftAssignmentRepository, never()).saveAll(anyList());
}
```

```java
@Test
void approveSwapRequestRejectsWhenEmployeesAlreadyHoldEachOthersShift() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Employee requester = employee(10L, "ACTIVE");
    Employee target = employee(20L, "ACTIVE");
    ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
    ShiftAssignment targetAssignment = publishedAssignment(200L, target);
    SwapRequest swapRequest = pendingApprovalSwapRequest(requesterAssignment, target, targetAssignment);
    when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount(principal)));
    when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
    when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(100L, 20L)).thenReturn(true);

    assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

    verify(shiftAssignmentRepository, never()).saveAll(anyList());
}
```

Helpers to add to `AdminSwapRequestServiceTest`:

```java
    private static SchedulePeriod publishedPeriod() {
        SchedulePeriod period = new SchedulePeriod();
        period.setStatus("PUBLISHED");
        return period;
    }

    private static ShiftAssignment publishedAssignment(Long id, Employee employee) {
        return assignment(id, employee, "PUBLISHED");
    }

    private static ShiftAssignment draftAssignment(Long id, Employee employee) {
        return assignment(id, employee, "DRAFT");
    }

    private static SwapRequest pendingApprovalSwapRequest(
            ShiftAssignment requesterAssignment, Employee target, ShiftAssignment targetAssignment) {
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(target)
                .targetAssignment(targetAssignment)
                .status("PENDING_APPROVAL")
                .build();
        swapRequest.setId(5L);
        return swapRequest;
    }
```

The class needs new mock fields and constructor wiring. Update the mock block at the top of the test class to:

```java
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
```

and pass it into the service constructor. Update the existing `approveSwapRequest` tests that assert only status/log to also stub `shiftAssignmentRepository.saveAll` and give the swap request a target assignment, otherwise they will start failing on the new required field.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=AdminSwapRequestServiceTest"`
Expected: FAIL - `shiftAssignmentRepository` does not exist / swap not performed.

- [ ] **Step 3: Add the dependency and the swap logic**

In `AdminSwapRequestService`, add field and constructor parameter `ShiftAssignmentRepository shiftAssignmentRepository` to both constructors, then add to `approveSwapRequest` right after the `PENDING_APPROVAL` status check:

```java
        exchangeAssignments(swapRequest);
```

Add these methods:

```java
    private void exchangeAssignments(SwapRequest swapRequest) {
        ShiftAssignment requesterAssignment = swapRequest.getRequesterAssignment();
        ShiftAssignment targetAssignment = swapRequest.getTargetAssignment();
        if (targetAssignment == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Yêu cầu đổi ca chưa có ca đối ứng nên không thể hoán đổi.");
        }
        requirePublishedPeriod(requesterAssignment);
        requirePublishedPeriod(targetAssignment);
        Employee requester = requesterAssignment.getEmployee();
        Employee target = targetEmployee(swapRequest, targetAssignment);
        if (shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(
                        requesterAssignment.getWorkShift().getId(), target.getId())
                || shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(
                        targetAssignment.getWorkShift().getId(), requester.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hai nhân viên đã cùng có ca ở hai ca cần hoán đổi nên không thể đổi chéo.");
        }
        requesterAssignment.setEmployee(target);
        requesterAssignment.setAssignmentStatus("SWAPPED");
        targetAssignment.setEmployee(requester);
        targetAssignment.setAssignmentStatus("SWAPPED");
        shiftAssignmentRepository.saveAll(List.of(requesterAssignment, targetAssignment));
    }

    private Employee targetEmployee(SwapRequest swapRequest, ShiftAssignment targetAssignment) {
        Employee declaredTarget = swapRequest.getTargetEmployee();
        if (declaredTarget == null || declaredTarget.getId() == null
                || !declaredTarget.getId().equals(targetAssignment.getEmployee().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ca đối ứng không thuộc nhân viên được yêu cầu đổi ca.");
        }
        return declaredTarget;
    }

    private static void requirePublishedPeriod(ShiftAssignment assignment) {
        if (assignment == null || assignment.getWorkShift() == null
                || assignment.getWorkShift().getSchedulePeriod() == null
                || !"PUBLISHED".equals(assignment.getWorkShift().getSchedulePeriod().getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ca không còn thuộc kỳ lịch đã công bố nên không thể hoán đổi.");
        }
    }
```

Add the import for `ShiftAssignmentRepository` and `ShiftAssignment`.

Also extend the audit log payload in `recordSwapReview` with `"targetAssignmentId", swapRequest.getTargetAssignment() == null ? null : swapRequest.getTargetAssignment().getId()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=AdminSwapRequestServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java
git commit -m "feat: exchange shift assignments when a swap is approved"
```

---

### Task 3: Frontend types and service

**Files:**
- Modify: `frontend/src/types/backendSwapRequest.ts`
- Modify: `frontend/src/services/backendSwapRequestService.ts`
- Modify: `frontend/src/types/management.ts`
- Test: `frontend/src/services/backendSwapRequestService.test.ts`

- [ ] **Step 1: Write the failing test**

Add to `backendSwapRequestService.test.ts`:

```ts
  test('sends the selected target assignment when the employee accepts', async () => {
    const fetchMock = ...
    const response = swapRequestResponse({ status: 'PENDING_APPROVAL', targetAssignmentId: 42 })

    await backendSwapRequestService.respondForEmployee('5', true, 42)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/employee/swap-requests/5/respond')
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({ accept: true, targetAssignmentId: 42 })
  })

  test('omits the target assignment when the employee rejects', async () => {
    const fetchMock = ...
    await backendSwapRequestService.respondForEmployee('5', false)

    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({ accept: false })
  })
```

Reuse the file's existing `jsonResponse` / `fetchMock` helpers rather than inventing new ones.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend; npm.cmd test -- backendSwapRequestService.test.ts`
Expected: FAIL - signature accepts 2 args.

- [ ] **Step 3: Update the types**

In `frontend/src/types/backendSwapRequest.ts`:

```ts
export interface BackendRespondSwapRequestRequest {
  accept: boolean
  targetAssignmentId?: number
}
```

- [ ] **Step 4: Update the service method**

In `frontend/src/services/backendSwapRequestService.ts` replace `respondForEmployee`:

```ts
  async respondForEmployee(id: string, accept: boolean, targetAssignmentId?: number): Promise<SwapRequest> {
    const body: BackendRespondSwapRequestRequest = accept
      ? { accept: true, targetAssignmentId }
      : { accept: false }
    const response = await requestJson<BackendSwapRequestResponse>(`/api/employee/swap-requests/${encodeURIComponent(id)}/respond`, { method: 'POST', body })
    return adaptBackendSwapRequest(response)
  },
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend; npm.cmd test -- backendSwapRequestService.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/backendSwapRequest.ts frontend/src/services/backendSwapRequestService.ts frontend/src/services/backendSwapRequestService.test.ts
git commit -m "feat: send the chosen target shift with the swap response"
```

---

### Task 4: Frontend respond dialog with shift picker

**Files:**
- Create: `frontend/src/pages/employee/EmployeeSwapRequestsPage.helpers.ts`
- Create: `frontend/src/pages/employee/EmployeeSwapRequestsPage.helpers.test.ts`
- Modify: `frontend/src/pages/employee/EmployeeSwapRequestsPage.tsx`
- Modify: `frontend/src/services/swapRequestService.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/employee/EmployeeSwapRequestsPage.helpers.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import {
  getSwapShiftOptionLabel,
  getSwapConfirmBlockedMessage,
  selectRespondTargetAssignment,
  type SwapShiftOption,
} from './EmployeeSwapRequestsPage.helpers'

const option = (overrides: Partial<SwapShiftOption> = {}): SwapShiftOption => ({
  assignmentId: '42',
  workDate: '2026-09-21',
  shiftName: 'Trực sáng',
  shiftStartTime: '07:00',
  shiftEndTime: '13:00',
  periodName: 'Tuần 39',
  periodStatus: 'PUBLISHED',
  ...overrides,
})

describe('EmployeeSwapRequestsPage helpers', () => {
  it('describes a shift option with date, times and period', () => {
    expect(getSwapShiftOptionLabel(option()))
      .toBe('2026-09-21 • Trực sáng 07:00-13:00 • Tuần 39')
  })

  it('blocks confirmation when accepting without a chosen shift', () => {
    expect(getSwapConfirmBlockedMessage(true, ''))
      .toBe('Vui lòng chọn ca của bạn để đổi trước khi xác nhận.')
  })

  it('does not block confirmation when rejecting', () => {
    expect(getSwapConfirmBlockedMessage(false, '')).toBe('')
  })

  it('does not block confirmation when a shift is chosen', () => {
    expect(getSwapConfirmBlockedMessage(true, '42')).toBe('')
  })

  it('ignores shifts outside published periods', () => {
    expect(selectRespondTargetAssignment([option({ assignmentId: '1' }), option({ assignmentId: '2', periodStatus: 'DRAFT' })], '1'))
      .toBe('1')
    expect(selectRespondTargetAssignment([option({ periodStatus: 'DRAFT' })], '1')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend; npm.cmd test -- EmployeeSwapRequestsPage.helpers.test.ts`
Expected: FAIL - module not found.

- [ ] **Step 3: Create the helpers file**

```ts
export interface SwapShiftOption {
  assignmentId: string
  workDate: string
  shiftName: string
  shiftStartTime: string
  shiftEndTime: string
  periodName: string
  periodStatus: string
}

export const getSwapShiftOptionLabel = (option: SwapShiftOption) =>
  `${option.workDate} • ${option.shiftName} ${option.shiftStartTime}-${option.shiftEndTime} • ${option.periodName}`

export const getSwapConfirmBlockedMessage = (accept: boolean, targetAssignmentId: string) =>
  accept && !targetAssignmentId ? 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' : ''

export const selectRespondTargetAssignment = (
  options: SwapShiftOption[],
  assignmentId: string,
) => options.find((option) => option.assignmentId === assignmentId && option.periodStatus === 'PUBLISHED')?.assignmentId ?? null
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend; npm.cmd test -- EmployeeSwapRequestsPage.helpers.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire the dialog into the page**

In `EmployeeSwapRequestsPage.tsx`:

- add `const [respondTarget, setRespondTarget] = useState<SwapRequest | null>(null)`
- add `const [respondShiftId, setRespondShiftId] = useState('')`
- add `const [respondShifts, setRespondShifts] = useState<SwapAssignmentOption[]>([])`
- add `const [respondLoading, setRespondLoading] = useState(false)`
- replace the accept icon button `onClick={() => handleRespond(item.id, true)}` with `onClick={() => { setRespondTarget(item); setRespondShiftId(''); void loadRespondShifts() }}`
- change `handleRespond` to take the target id explicitly:

```ts
  const loadRespondShifts = async () => {
    setRespondLoading(true)
    try {
      setRespondShifts(await swapRequestService.listMyAssignments())
    } catch {
      setRespondShifts([])
    } finally {
      setRespondLoading(false)
    }
  }

  const handleRespond = async (id: string, accept: boolean, targetAssignmentId?: number) => {
    try {
      await swapRequestService.respondMine(id, accept, targetAssignmentId)
      setRespondTarget(null)
      await loadData()
    } catch (respondError) {
      showDialog({ variant: 'error', description: respondError instanceof Error ? respondError.message : 'Không thể phản hồi yêu cầu.' })
    }
  }
```

- add a `ConfirmDialog` after the detail dialog:

```tsx
      <ConfirmDialog
        open={Boolean(respondTarget)}
        title="Xác nhận đổi ca"
        description="Chọn ca của bạn để đổi lấy ca của nhân viên gửi yêu cầu. Yêu cầu sẽ chờ quản lý duyệt."
        onCancel={() => setRespondTarget(null)}
        onConfirm={() => {
          if (!respondTarget) return
          const blocked = getSwapConfirmBlockedMessage(true, respondShiftId)
          if (blocked) {
            showDialog({ variant: 'warning', description: blocked })
            return
          }
          void handleRespond(respondTarget.id, true, Number(respondShiftId))
        }}
        confirmLabel="Gửi quản lý duyệt"
      >
        <div className="mt-4 space-y-2">
          <label className="text-sm text-slate-700">Ca của bạn để đổi</label>
          <select value={respondShiftId} disabled={respondLoading} onChange={(event) => setRespondShiftId(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm disabled:opacity-50">
            <option value="">{respondLoading ? 'Đang tải ca làm...' : 'Chọn ca của bạn'}</option>
            {respondShifts.filter((shift) => shift.periodStatus === 'PUBLISHED').map((shift) => (
              <option key={shift.id} value={shift.id}>{getSwapShiftOptionLabel(shift)}</option>
            ))}
          </select>
        </div>
      </ConfirmDialog>
```

- remove the line `Không cần chọn ca của nhân viên đối ứng trong giai đoạn này (targetAssignment không bắt buộc).` from the create dialog, since it is no longer true.
- import the three helpers from `./EmployeeSwapRequestsPage.helpers`.

In `frontend/src/services/swapRequestService.ts`, change the employee respond wrapper to forward the third argument:

```ts
  async respondMine(id: string, accept: boolean, targetAssignmentId?: number) {
    return backendMode
      ? backendSwapRequestService.respondForEmployee(id, accept, targetAssignmentId)
      : ...
  }
```

Match the existing mock fallback shape in that file for the non-backend branch.

- [ ] **Step 6: Run frontend tests and build**

Run: `cd frontend; npm.cmd test`
Expected: all pass.
Run: `cd frontend; npm.cmd run build`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/employee/EmployeeSwapRequestsPage.tsx frontend/src/pages/employee/EmployeeSwapRequestsPage.helpers.ts frontend/src/pages/employee/EmployeeSwapRequestsPage.helpers.test.ts frontend/src/services/swapRequestService.ts
git commit -m "feat: let the targeted employee pick a shift to swap"
```

---

### Task 5: Full verification

**Files:** none.

- [ ] **Step 1: Backend full test suite**

Run: `cd backend; ./mvnw.cmd -q test`
Expected: `BACKEND_EXIT=0`.

- [ ] **Step 2: Frontend test, build, lint**

Run: `cd frontend; npm.cmd test`
Expected: all pass.
Run: `cd frontend; npm.cmd run build`
Expected: exit 0.
Run: `cd frontend; npm.cmd run lint`
Expected: `0 errors`, at most the pre-existing `AuthContext.tsx` warning.

- [ ] **Step 3: AI service regression check**

Run: `cd ai-service; .\.venv\Scripts\pytest.exe -q`
Expected: unchanged pass count.

- [ ] **Step 4: Manual end-to-end walkthrough**

1. Publish a schedule containing one shift for `emp001` and one for `emp002`.
2. As `emp001`, create a swap request naming `emp002`.
3. Sign in as `emp002`; confirm the dialog now requires choosing a shift and blocks empty confirmation.
4. As manager, approve; verify `shift_assignments` rows for both employees now point at each other's shifts and both are `SWAPPED`.

---

## Self-review notes

- Spec coverage: mandatory target shift (Task 1 + 4), real assignment exchange on approve (Task 2), frontend payload (Task 3), UI (Task 4), verification (Task 5).
- Reject path intentionally keeps `targetAssignmentId` optional, so declining never requires a selection.
- `targetAssignment` remains nullable in the entity and DB so historical `NULL` rows stay readable; only the accept path enforces it.

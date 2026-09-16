# Period Lifecycle APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add manager-only approve, publish, and cancel endpoints for schedule periods with strict staffing validation and extended list filters.

**Architecture:** Extend `AdminSchedulePeriodService`/`AdminSchedulePeriodController` in place (approach A from the approved spec). Staffing validation reuses `WorkShiftRepository.searchForPeriod` and `ShiftAssignmentRepository.listForPeriod`; no new repository, no DTO change.

**Tech Stack:** Java 17, Spring Boot (WebMVC, Data JPA), JUnit 5, AssertJ, Mockito, MockMvc.

**Working directory:** All commands run from the repo root (`scheduling_platform/`). Maven commands use `.\backend\mvnw.cmd -f backend/pom.xml` because the wrapper and POM both live in `backend/` (verified: running the wrapper from the root without `-f` fails with `MissingProjectException`). In PowerShell, quote every `-D` argument (e.g. `"-Dtest=..." "-Dsurefire.failIfNoSpecifiedTests=true"`) — unquoted dotted args get split and Maven fails with `Unknown lifecycle phase`.

**Spec:** `docs/superpowers/specs/2026-09-17-period-lifecycle-apis-design.md`

---

## File Map

- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java` — constructor gains `WorkShiftRepository` + `ShiftAssignmentRepository`; new methods `approveSchedulePeriod`, `publishSchedulePeriod`, `cancelSchedulePeriod`, `validateStaffing`; `normalizeStatus` accepts all four lifecycle states.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java` — three `PATCH` endpoints (`/{periodId}/approve`, `/{periodId}/publish`, `/{periodId}/cancel`).
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java` — 4-arg service construction; existing `listSchedulePeriodsRejectsUnsupportedStatus` switches from `"PUBLISHED"` to `"ARCHIVED"`; new lifecycle tests plus `workShift`/`assignment`/`employee` helpers.
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java` — MockMvc tests for the three endpoints (happy path + security).
- Modify: `docs/project_plan.md` — mark the approve/publish slice done after green verification.

---

### Task 1: Wire new repositories into the service

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Add repository fields and constructor parameters to the service**

Replace the field/constructor block in `AdminSchedulePeriodService.java`:

```java
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AccountRepository accountRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public AdminSchedulePeriodService(
            SchedulePeriodRepository schedulePeriodRepository,
            AccountRepository accountRepository,
            WorkShiftRepository workShiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.accountRepository = accountRepository;
        this.workShiftRepository = workShiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }
```

Add imports:

```java
import scheduling.scheduling.repository.WorkShiftRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
```

- [ ] **Step 2: Update the existing test to use the 4-arg constructor**

In `AdminSchedulePeriodServiceTest.java`, replace:

```java
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminSchedulePeriodService service = new AdminSchedulePeriodService(schedulePeriodRepository, accountRepository);
```

with:

```java
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final AdminSchedulePeriodService service = new AdminSchedulePeriodService(
            schedulePeriodRepository, accountRepository, workShiftRepository, shiftAssignmentRepository);
```

Add imports:

```java
import scheduling.scheduling.repository.WorkShiftRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
```

- [ ] **Step 3: Compile main and test sources**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q -DskipTests compile test-compile`
Expected: BUILD SUCCESS (exit code 0, quiet output).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "refactor: wire shift repositories into period service"
```

---

### Task 2: Extend the list status filter

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Fix the existing unsupported-status test (PUBLISHED becomes legal)**

In `AdminSchedulePeriodServiceTest.java`, replace `"PUBLISHED"` with `"ARCHIVED"` in `listSchedulePeriodsRejectsUnsupportedStatus`:

```java
    @Test
    void listSchedulePeriodsRejectsUnsupportedStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(principal, "ARCHIVED", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
```

- [ ] **Step 2: Add the failing test for new accepted statuses**

```java
    @Test
    void listSchedulePeriodsAcceptsLifecycleStatuses() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        service.listSchedulePeriods(principal, " approved ", null, null);
        service.listSchedulePeriods(principal, "PUBLISHED", null, null);
        service.listSchedulePeriods(principal, "CANCELLED", null, null);

        verify(schedulePeriodRepository).searchForAdmin("APPROVED", null, null);
        verify(schedulePeriodRepository).searchForAdmin("PUBLISHED", null, null);
        verify(schedulePeriodRepository).searchForAdmin("CANCELLED", null, null);
    }
```

- [ ] **Step 3: Run the new test to verify it fails**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodServiceTest#listSchedulePeriodsAcceptsLifecycleStatuses -Dsurefire.failIfNoSpecifiedTests=true`
Expected: FAIL — `searchForAdmin("APPROVED", ...)` never invoked (normalizeStatus throws 400 for non-DRAFT).

- [ ] **Step 4: Implement the extended normalizeStatus**

In `AdminSchedulePeriodService.java`, add constants and replace `normalizeStatus`:

```java
    private static final String APPROVED = "APPROVED";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";
    private static final java.util.Set<String> LISTABLE_STATUSES =
            java.util.Set.of(DRAFT, APPROVED, PUBLISHED, CANCELLED);
```

```java
    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return DRAFT;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!LISTABLE_STATUSES.contains(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }
```

- [ ] **Step 5: Run the service test class**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodServiceTest -Dsurefire.failIfNoSpecifiedTests=true`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "feat: allow listing schedule periods in all lifecycle states"
```

---

### Task 3: Approve endpoint (service)

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Add test helpers for shifts and assignments**

Append these helpers to `AdminSchedulePeriodServiceTest.java` (next to `draftPeriod`):

```java
    private static WorkShift staffedShift(Long id, int requiredStaff) {
        WorkShift shift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(requiredStaff)
                .build();
        shift.setId(id);
        return shift;
    }

    private static Employee activeEmployee(Long id, String code, String name) {
        Employee employee = Employee.builder()
                .employeeCode(code)
                .fullName(name)
                .status("ACTIVE")
                .build();
        employee.setId(id);
        return employee;
    }

    private static ShiftAssignment shiftAssignment(Long id, WorkShift shift, Employee employee) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        assignment.setId(id);
        return assignment;
    }
```

Add imports:

```java
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.WorkShift;
```

- [ ] **Step 2: Write failing approve tests**

```java
    @Test
    void approveSchedulePeriodApprovesFullyStaffedDraft() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        WorkShift shift = staffedShift(200L, 2);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first), shiftAssignment(301L, shift, second)));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.approveSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedByAccountId()).isEqualTo(1L);
        assertThat(response.approvedByUsername()).isEqualTo("manager01");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void approveSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.approveSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void approveSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void approveSchedulePeriodRejectsPublishedAndCancelledPeriods() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        SchedulePeriod published = draftPeriod(20L, account(principal));
        published.setStatus("PUBLISHED");
        SchedulePeriod cancelled = draftPeriod(21L, account(principal));
        cancelled.setStatus("CANCELLED");
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(published));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveSchedulePeriodRejectsEmptyPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of());

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveSchedulePeriodRejectsUnderstaffedShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        WorkShift shift = staffedShift(200L, 2);
        Employee only = activeEmployee(10L, "EMP010", "Employee Ten");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, only)));

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void reapproveSchedulePeriodValidatesStaffingAgain() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        Account originalApprover = account(new CurrentAccountPrincipal(3L, "approver01", AccountRole.MANAGER));
        SchedulePeriod passing = draftPeriod(20L, manager);
        passing.setStatus("APPROVED");
        passing.setApprovedBy(originalApprover);
        SchedulePeriod failing = draftPeriod(21L, manager);
        failing.setStatus("APPROVED");
        failing.setApprovedBy(originalApprover);
        WorkShift shift = staffedShift(200L, 2);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(passing));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(failing));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(workShiftRepository.searchForPeriod(21L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first), shiftAssignment(301L, shift, second)));
        when(shiftAssignmentRepository.listForPeriod(21L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first)));

        AdminSchedulePeriodResponse response = service.approveSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedByAccountId()).isEqualTo(3L);
        assertThat(response.approvedByUsername()).isEqualTo("approver01");
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
```

- [ ] **Step 3: Run the new approve tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest='AdminSchedulePeriodServiceTest#approveSchedulePeriod*+reapproveSchedulePeriodValidatesStaffingAgain' -Dsurefire.failIfNoSpecifiedTests=true`
Expected: FAIL — compilation error, `approveSchedulePeriod` does not exist.

- [ ] **Step 4: Implement approve in the service**

Add to `AdminSchedulePeriodService.java` (after `updateSchedulePeriod`):

```java
    @Transactional
    public AdminSchedulePeriodResponse approveSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        Account manager = requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus()) && !APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        validateStaffing(periodId);
        if (APPROVED.equals(period.getStatus())) {
            return AdminSchedulePeriodResponse.from(period);
        }
        period.setStatus(APPROVED);
        period.setApprovedBy(manager);
        return AdminSchedulePeriodResponse.from(savePeriod(period));
    }

    private void validateStaffing(Long periodId) {
        List<WorkShift> shifts = workShiftRepository.searchForPeriod(periodId, null, null);
        if (shifts.isEmpty()) {
            throw invalidRequest();
        }
        java.util.Map<Long, Long> assignmentCounts = shiftAssignmentRepository.listForPeriod(periodId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        assignment -> assignment.getWorkShift().getId(), java.util.stream.Collectors.counting()));
        for (WorkShift shift : shifts) {
            if (assignmentCounts.getOrDefault(shift.getId(), 0L) < shift.getRequiredStaff()) {
                throw invalidRequest();
            }
        }
    }
```

Add imports:

```java
import scheduling.scheduling.entity.WorkShift;
```

- [ ] **Step 5: Run the service test class**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodServiceTest -Dsurefire.failIfNoSpecifiedTests=true`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "feat: add approve schedule period with staffing validation"
```

---

### Task 4: Publish endpoint (service)

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Write failing publish tests**

```java
    @Test
    void publishSchedulePeriodPublishesApprovedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("APPROVED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.publishSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void publishSchedulePeriodRejectsNonApprovedPeriods() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        SchedulePeriod draft = draftPeriod(20L, account(principal));
        SchedulePeriod cancelled = draftPeriod(21L, account(principal));
        cancelled.setStatus("CANCELLED");
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draft));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void republishSchedulePeriodKeepsOriginalPublishedAt() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 21, 9, 0);
        period.setPublishedAt(publishedAt);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.publishSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.publishSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }
```

- [ ] **Step 2: Run the new publish tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest='AdminSchedulePeriodServiceTest#publishSchedulePeriod*+republishSchedulePeriodKeepsOriginalPublishedAt' -Dsurefire.failIfNoSpecifiedTests=true`
Expected: FAIL — compilation error, `publishSchedulePeriod` does not exist.

- [ ] **Step 3: Implement publish in the service**

Add after `approveSchedulePeriod`:

```java
    @Transactional
    public AdminSchedulePeriodResponse publishSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (PUBLISHED.equals(period.getStatus())) {
            return AdminSchedulePeriodResponse.from(period);
        }
        if (!APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        period.setStatus(PUBLISHED);
        period.setPublishedAt(java.time.LocalDateTime.now());
        return AdminSchedulePeriodResponse.from(savePeriod(period));
    }
```

- [ ] **Step 4: Run the service test class**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodServiceTest -Dsurefire.failIfNoSpecifiedTests=true`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "feat: add publish schedule period endpoint logic"
```

---

### Task 5: Cancel endpoint (service)

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Write failing cancel tests**

```java
    @Test
    void cancelSchedulePeriodCancelsDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void cancelSchedulePeriodCancelsApprovedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("APPROVED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void cancelSchedulePeriodRejectsPublishedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.cancelSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void recancelSchedulePeriodIsIdempotent() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("CANCELLED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.cancelSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }
```

- [ ] **Step 2: Run the new cancel tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest='AdminSchedulePeriodServiceTest#cancelSchedulePeriod*+recancelSchedulePeriodIsIdempotent' -Dsurefire.failIfNoSpecifiedTests=true`
Expected: FAIL — compilation error, `cancelSchedulePeriod` does not exist.

- [ ] **Step 3: Implement cancel in the service**

Add after `publishSchedulePeriod`:

```java
    @Transactional
    public AdminSchedulePeriodResponse cancelSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (CANCELLED.equals(period.getStatus())) {
            return AdminSchedulePeriodResponse.from(period);
        }
        if (!DRAFT.equals(period.getStatus()) && !APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        period.setStatus(CANCELLED);
        return AdminSchedulePeriodResponse.from(savePeriod(period));
    }
```

- [ ] **Step 4: Run the service test class**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodServiceTest -Dsurefire.failIfNoSpecifiedTests=true`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "feat: add cancel schedule period endpoint logic"
```

---

### Task 6: Controller endpoints

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests for the three endpoints**

Add imports for `patch`:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

Add tests:

```java
    @Test
    void managerApprovesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.approveSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(adminSchedulePeriodService).approveSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void managerPublishesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.publishSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(adminSchedulePeriodService).publishSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void managerCancelsSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.cancelSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(adminSchedulePeriodService).cancelSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void nonManagerCannotApproveSchedulePeriod() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void anonymousCannotApproveSchedulePeriod() throws Exception {
        mockMvc.perform(patch("/api/admin/schedule-periods/20/approve"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void nonManagerCannotPublishSchedulePeriod() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void anonymousCannotPublishSchedulePeriod() throws Exception {
        mockMvc.perform(patch("/api/admin/schedule-periods/20/publish"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void nonManagerCannotCancelSchedulePeriod() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(patch("/api/admin/schedule-periods/20/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void anonymousCannotCancelSchedulePeriod() throws Exception {
        mockMvc.perform(patch("/api/admin/schedule-periods/20/cancel"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminSchedulePeriodService);
    }
```

- [ ] **Step 2: Run the new controller tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest='AdminSchedulePeriodControllerTest#managerApprovesSchedulePeriod+managerPublishesSchedulePeriod+managerCancelsSchedulePeriod+nonManagerCannotApproveSchedulePeriod+anonymousCannotApproveSchedulePeriod+nonManagerCannotPublishSchedulePeriod+anonymousCannotPublishSchedulePeriod+nonManagerCannotCancelSchedulePeriod+anonymousCannotCancelSchedulePeriod' -Dsurefire.failIfNoSpecifiedTests=true`
Expected: FAIL — 404 for the PATCH paths (no handler methods yet).

- [ ] **Step 3: Implement the three controller endpoints**

Add the `PatchMapping` import:

```java
import org.springframework.web.bind.annotation.PatchMapping;
```

Add methods after `updateSchedulePeriod`:

```java
    @PatchMapping("/{periodId}/approve")
    ResponseEntity<AdminSchedulePeriodResponse> approveSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.approveSchedulePeriod(principal, periodId));
    }

    @PatchMapping("/{periodId}/publish")
    ResponseEntity<AdminSchedulePeriodResponse> publishSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.publishSchedulePeriod(principal, periodId));
    }

    @PatchMapping("/{periodId}/cancel")
    ResponseEntity<AdminSchedulePeriodResponse> cancelSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.cancelSchedulePeriod(principal, periodId));
    }
```

- [ ] **Step 4: Run the controller test class**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test -Dtest=AdminSchedulePeriodControllerTest -Dsurefire.failIfNoSpecifiedTests=true`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java
git commit -m "feat: add approve publish cancel period endpoints"
```

---

### Task 7: Full verification and status docs

**Files:**
- Modify: `docs/project_plan.md`
- Test: full backend suite

- [ ] **Step 1: Run the full backend suite**

Run: `.\backend\mvnw.cmd -f backend/pom.xml clean test`
Expected: all tests pass, 0 failures/errors.

- [ ] **Step 2: Check the working tree is diff-clean**

Run: `git diff --check`
Expected: no output.

- [ ] **Step 3: Update Phase 5 status in project plan**

In `docs/project_plan.md`, mark the approve/publish/cancel slice done alongside the completed WorkShift and ShiftAssignment slices (keep the remaining Phase 5 items — update-in-place, view-by-employee, AI orchestration — untouched).

- [ ] **Step 4: Commit**

```bash
git add docs/project_plan.md
git commit -m "docs: update phase 5 lifecycle status"
```

---

## Spec Coverage

- `DRAFT -> APPROVED -> PUBLISHED`, `DRAFT/APPROVED -> CANCELLED`, no backward moves: Tasks 3, 4, 5.
- Idempotent repeats (re-approve re-validates without touching `approvedBy` or saving, publish keeps `publishedAt`, cancel no-op): Tasks 3, 4, 5.
- Approve staffing gate (non-empty period, every shift meets `requiredStaff`): Task 3.
- `approvedBy` on approve, `publishedAt` on publish: Tasks 3, 4.
- Freeze effects: automatic via existing `DRAFT` guards — no code change, covered by existing tests.
- List filter extension (`APPROVED`/`PUBLISHED`/`CANCELLED`, default `DRAFT`): Task 2.
- Response shape: DTO already exposes fields — no change; verified by assertions on `approvedByAccountId`/`publishedAt` in Tasks 3–4.
- Error mapping (400/403/404): Tasks 3, 4, 5, 6.
- Controller security (manager 200, non-manager 403, anonymous 401): Task 6.
- Full verification: Task 7.

## Self-Review

- Spec coverage: every spec section maps to a task above; freeze effects need no new code.
- Placeholder scan: no TBD/TODO; every code step shows exact code, exact commands, exact expected output.
- Type consistency: method names `approveSchedulePeriod`/`publishSchedulePeriod`/`cancelSchedulePeriod` match between tests, service, and controller in all tasks; `LocalDateTime` already imported in the service test; `Employee`/`ShiftAssignment`/`WorkShift` imports added in Task 3 Step 1 before first use.

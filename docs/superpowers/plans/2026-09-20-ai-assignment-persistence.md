# AI Assignment Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist valid AI SUCCESS assignments into `ShiftAssignment` (replace unlocked AUTO only, preserve MANUAL + locked), with rollback + 502 on apply failure.

**Architecture:** Builder sends preserved assignments (locked OR MANUAL) as AI fixed inputs; a new transactional `AiAssignmentPersistenceService` deletes unlocked AUTO, validates preserved + AI hard invariants, and inserts AUTO rows; `AdminOptimizationService` calls it after protocol pass on SUCCESS and maps apply failure to run FAILED + 502.

**Tech Stack:** Java 21, Spring Boot 4.1.1 WebMVC, Spring Data JPA, JUnit 6, Mockito, AssertJ, MockMvc, Docker Postgres 16 for the real transaction test.

---

## File Structure

- Modify `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`: rename `findLockedForPeriod` to `findFixedForPeriod` (locked OR MANUAL) with same fetch joins.
- Modify `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`: use `findFixedForPeriod`; rename snapshot component `lockedAssignments` to `fixedAssignments`; validation message becomes `Preserved assignment <id> references inactive scheduling data`.
- Modify `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`: update 5 `findLockedForPeriod` stubs, message assertions, `testSnapshot()` helper, and `.lockedAssignments()` accessors; add MANUAL-inclusion test.
- Create `backend/src/main/java/scheduling/scheduling/ai/AiApplyException.java`: runtime exception for apply failures.
- Create `backend/src/main/java/scheduling/scheduling/ai/AiAssignmentPersistenceService.java`: transactional delete + validate + insert, returns `ApplyResult`.
- Create `backend/src/main/java/scheduling/scheduling/ai/ApplyResult.java`: record `(int inserted, int skipped)`.
- Create `backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceServiceTest.java`: mocked-repo unit tests.
- Create `backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceTransactionTest.java`: real JPA rollback test.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`: inject persistence service, tighten protocol validation, call persistence on SUCCESS, map apply failure to FAILED + 502.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`: update constructor, stub persistence in existing SUCCESS tests, add new protocol/persistence tests.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`: only if context fails (new bean needs repo mock).
- Modify `docs/project_plan.md`: tick Slice 6B items only after tests pass.

## Task 1: Builder Sends Preserved Assignments as Fixed Inputs

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`
- Modify: `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`

- [ ] **Step 1: Rename the repository query**

Replace `findLockedForPeriod` with `findFixedForPeriod` (same fetch joins, wider filter):

```java
@Query("""
        select sa from ShiftAssignment sa
        join fetch sa.employee e
        join fetch sa.workShift ws
        join fetch ws.shiftType st
        where ws.schedulePeriod.id = :periodId
          and (sa.locked = true or sa.assignedSource = 'MANUAL')
        order by sa.id asc
        """)
List<ShiftAssignment> findFixedForPeriod(@Param("periodId") Long periodId);
```

Delete the old `findLockedForPeriod` method. No other main code calls it (verified: only `AiSchedulingPayloadBuilder.java:170`).

- [ ] **Step 2: Update the builder**

In `AiSchedulingPayloadBuilder.java`:
- Change line 170 call to `shiftAssignmentRepository.findFixedForPeriod(period.getId())`.
- Rename local variable `lockedAssignments` to `fixedAssignments` in `loadAndValidate`.
- Change the validation message to `"Preserved assignment " + assignment.getId() + " references inactive scheduling data"`.
- Rename the `PayloadSnapshot` component `lockedAssignments` to `fixedAssignments` (record definition line 337-345).
- Update `buildFrom` mapping (lines 118-123) to use `snapshot.fixedAssignments()` (DTO field name `lockedAssignments` in `AiSchedulingRequestDto` stays unchanged for AI schema compatibility).

- [ ] **Step 3: Update existing builder tests**

In `AiSchedulingPayloadBuilderTest.java`:
- Replace all 5 `when(shiftAssignmentRepository.findLockedForPeriod(5L))` stubs (lines ~176, ~204, ~323, ~354, ~490) with `findFixedForPeriod(5L)`.
- Update message assertions expecting `"Locked assignment 601 references inactive scheduling data"` to `"Preserved assignment 601 references inactive scheduling data"` (check exact id used in each test).
- Update `testSnapshot()` helper (line ~518) and any `.lockedAssignments()` accessor to `fixedAssignments`.
- Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest test`
- Expected: PASS (behavior identical for locked-only fixtures).

- [ ] **Step 4: Add MANUAL-inclusion tests**

Add tests with these exact names:

```java
@Test void includesManualAssignmentsAsFixedInputs()
@Test void rejectsFixedAssignmentReferencingInactiveEmployee()
```

`includesManualAssignmentsAsFixedInputs`: fixture with one locked AUTO assignment and one unlocked MANUAL assignment for the period; stub `findFixedForPeriod(5L)` to return both; assert payload `lockedAssignments()` contains both pairs and MANUAL row maps to its `assignmentId/employeeId/workShiftId`.

`rejectsFixedAssignmentReferencingInactiveEmployee`: MANUAL assignment whose employee is not in the schedulable set; assert `ResponseStatusException` BAD_REQUEST with reason `"Preserved assignment <id> references inactive scheduling data"`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest test`

Expected: PASS (14 tests: 12 existing + 2 new).

- [ ] **Step 5: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java && git commit -m "feat: send preserved assignments as AI fixed inputs"`

## Task 2: Persistence Service With TDD

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/ai/AiApplyException.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/ApplyResult.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/AiAssignmentPersistenceService.java`
- Create: `backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceServiceTest.java`

- [ ] **Step 1: Create exception and result types**

```java
package scheduling.scheduling.ai;

public class AiApplyException extends RuntimeException {
    public AiApplyException(String message) {
        super(message);
    }

    public AiApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
package scheduling.scheduling.ai;

public record ApplyResult(int inserted, int skipped) {}
```

- [ ] **Step 2: Write failing delete-only test**

Test name: `deletesUnlockedAutoAndPreservesManualAndLocked()`.

Mock `ShiftAssignmentRepository` with Mockito `mock()`. The service takes the snapshot as input, so construct it directly as a public record — no builder call needed. Fixture: period id 5; `listForPeriod(5L)` returns 3 rows: unlocked AUTO (id 1), unlocked MANUAL (id 2), locked AUTO (id 3). Snapshot: `new AiSchedulingPayloadBuilder.PayloadSnapshot(employees, shiftTypes, workShifts, leaves, preferences, fixedAssignments, rules, solverOptions)` with one schedulable employee (id 10), one active shift type, one work shift (id 100, requiredStaff 2, with the shift type and `schedulePeriod` set), empty leaves/preferences/fixed lists, rules with `hardRules.maxWorkHoursPerDay = 12` (other rule fields any valid ints/weights), and solver options `(30, 1, 8)`. Build minimal entities with `Employee.builder()` / `WorkShift.builder()` / `ShiftType.builder()` setting ids via `setId` (BaseEntity); every `ShiftAssignment` returned by `listForPeriod` must point at a `WorkShift` whose `schedulePeriod.id` is 5 because the service filters defensively by period id. Set `assignmentStatus`, `assignedSource`, `locked` explicitly on each assignment. AI response: SUCCESS with empty assignments list.

Expected: `deleteAll` called with exactly the unlocked AUTO row; `saveAll` called with empty list (or never called); result `inserted == 0`, `skipped == 0`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiAssignmentPersistenceServiceTest#deletesUnlockedAutoAndPreservesManualAndLocked test`

Expected: FAIL because `AiAssignmentPersistenceService` does not exist.

- [ ] **Step 3: Implement minimal service**

Constructor dependency: `ShiftAssignmentRepository` only. All reference data comes from the snapshot.

```java
package scheduling.scheduling.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class AiAssignmentPersistenceService {

    private static final String AUTO = "AUTO";
    private static final String MANUAL = "MANUAL";
    private static final String ASSIGNED = "ASSIGNED";

    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public AiAssignmentPersistenceService(ShiftAssignmentRepository shiftAssignmentRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }

    @Transactional
    public ApplyResult apply(
            SchedulePeriod period,
            AiSchedulingPayloadBuilder.PayloadSnapshot snapshot,
            AiSchedulingResponseDto response) {
        List<ShiftAssignment> current = shiftAssignmentRepository.listForPeriod(period.getId());
        List<ShiftAssignment> toDelete = new ArrayList<>();
        List<ShiftAssignment> preserved = new ArrayList<>();
        for (ShiftAssignment assignment : current) {
            if (assignment.getWorkShift() == null
                    || !period.getId().equals(assignment.getWorkShift().getSchedulePeriod().getId())) {
                continue;
            }
            if (AUTO.equals(assignment.getAssignedSource()) && !Boolean.TRUE.equals(assignment.getLocked())) {
                toDelete.add(assignment);
            } else {
                preserved.add(assignment);
            }
        }
        shiftAssignmentRepository.deleteAll(toDelete);

        Map<Long, WorkShift> shiftsById = new HashMap<>();
        for (WorkShift shift : snapshot.workShifts()) {
            shiftsById.put(shift.getId(), shift);
        }
        Set<Long> schedulableEmployeeIds = new HashSet<>();
        for (Employee employee : snapshot.employees()) {
            schedulableEmployeeIds.add(employee.getId());
        }

        Set<String> preservedPairs = new HashSet<>();
        Map<Long, List<ShiftAssignment>> preservedByShift = new HashMap<>();
        Map<Long, List<ShiftAssignment>> preservedByEmployee = new HashMap<>();
        for (ShiftAssignment assignment : preserved) {
            String pair = pairKey(assignment.getEmployee().getId(), assignment.getWorkShift().getId());
            preservedPairs.add(pair);
            preservedByShift.computeIfAbsent(assignment.getWorkShift().getId(), key -> new ArrayList<>())
                    .add(assignment);
            preservedByEmployee.computeIfAbsent(assignment.getEmployee().getId(), key -> new ArrayList<>())
                    .add(assignment);
        }

        List<ShiftAssignment> toInsert = new ArrayList<>();
        Set<String> seenAiPairs = new HashSet<>();
        Map<Long, Integer> newCountByShift = new HashMap<>();
        Map<Long, List<WorkShift>> newShiftsByEmployee = new HashMap<>();
        int skipped = 0;
        for (AiSchedulingResponseDto.AssignmentDto item : response.assignments()) {
            if (item == null || item.employeeId() == null || item.workShiftId() == null) {
                throw new AiApplyException("AI assignment has null item, employee, or shift");
            }
            String pair = pairKey(item.employeeId(), item.workShiftId());
            if (!seenAiPairs.add(pair)) {
                throw new AiApplyException(
                        "Duplicate AI assignment for employee " + item.employeeId()
                                + " and shift " + item.workShiftId());
            }
            if (preservedPairs.contains(pair)) {
                skipped++;
                continue;
            }
            WorkShift shift = shiftsById.get(item.workShiftId());
            if (shift == null) {
                throw new AiApplyException("AI assignment references unknown shift " + item.workShiftId());
            }
            if (!schedulableEmployeeIds.contains(item.employeeId())) {
                throw new AiApplyException("AI assignment references unknown employee " + item.employeeId());
            }
            int preservedCount = preservedByShift.getOrDefault(item.workShiftId(), List.of()).size();
            int newCount = newCountByShift.getOrDefault(item.workShiftId(), 0) + 1;
            if (shift.getRequiredStaff() != null && preservedCount + newCount > shift.getRequiredStaff()) {
                throw new AiApplyException("AI assignments exceed required staff for shift " + item.workShiftId());
            }
            newCountByShift.put(item.workShiftId(), newCount);
            Employee employee = findEmployee(snapshot, item.employeeId());
            checkConflicts(item, shift, employee, snapshot, preservedByEmployee, newShiftsByEmployee);
            newShiftsByEmployee.computeIfAbsent(item.employeeId(), key -> new ArrayList<>()).add(shift);
            toInsert.add(ShiftAssignment.builder()
                    .workShift(shift)
                    .employee(employee)
                    .assignmentStatus(ASSIGNED)
                    .assignedSource(AUTO)
                    .locked(false)
                    .build());
        }

        try {
            shiftAssignmentRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException ex) {
            throw new AiApplyException("AI assignments violate assignment uniqueness", ex);
        } catch (RuntimeException ex) {
            throw new AiApplyException("Failed to persist AI assignments", ex);
        }
        return new ApplyResult(toInsert.size(), skipped);
    }
}
```

`checkConflicts` (private): for the AI (employee, shift) pair, verify against preserved assignments of that employee AND already-accepted new shifts of that employee:
- overlap: reuse interval logic — private static `interval(WorkShift)` identical to `AdminShiftAssignmentService.interval` (workDate + shiftType start/end, overnight rollover). If overlapping → `AiApplyException("AI assignment overlaps existing assignment for employee ...")`.
- leave: snapshot.leaves() where `employee.id` matches and `!shift.workDate.isBefore(leave.startDate) && !shift.workDate.isAfter(leave.endDate)` → `AiApplyException("AI assignment conflicts with approved leave ...")`.
- daily hours: sum workMinutes (persisted `workMinutes` else `durationHours*60`) for same workDate across preserved + new shifts + candidate; limit from `snapshot.rules().hardRules().maxWorkHoursPerDay() * 60`; exceed → `AiApplyException("AI assignments exceed maximum work hours ...")`.

`findEmployee(snapshot, id)`: linear scan of `snapshot.employees()`; throw `AiApplyException` unknown employee if absent (defensive; ids already checked).

Wrap repository write failures in `AiApplyException` so every apply failure path reaches the `AdminOptimizationService` apply-failure catch and records `finishFailure(..., response)` per the spec. Validation failures already throw `AiApplyException`; if `deleteAll` can throw in implementation, wrap the whole delete/validate/insert body in `try/catch (RuntimeException ex)` and rethrow existing `AiApplyException` unchanged.

`pairKey(employeeId, workShiftId)`: `employeeId + ":" + workShiftId`.

Do NOT use bulk `@Modifying` delete: load + `deleteAll` keeps the persistence context consistent (spec §5 decision).

- [ ] **Step 4: Run delete-only test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiAssignmentPersistenceServiceTest#deletesUnlockedAutoAndPreservesManualAndLocked test`

Expected: PASS.

- [ ] **Step 5: Add failing validation/insert tests**

Add tests with these exact names:

```java
@Test void insertsAiAssignmentsAsAutoAssignedUnlocked()
@Test void skipsAiAssignmentsCoveredByPreservedPairs()
@Test void rejectsUnknownEmployee()
@Test void rejectsUnknownShift()
@Test void rejectsDuplicateAiPairs()
@Test void rejectsOverstaffedShift()
@Test void rejectsOverlappingPreservedAssignment()
@Test void rejectsApprovedLeaveConflict()
@Test void rejectsDailyHoursExceeded()
@Test void wrapsUniqueViolationAsApplyException()
```

Each rejection test asserts `AiApplyException` with a message containing the key noun (`employee`, `shift`, `Duplicate`, `required staff`, `overlaps`, `leave`, `work hours`, `uniqueness`) and verifies `saveAll` was never called (proving no partial persist at unit level; real rollback is covered by the transaction test in Task 4).

`insertsAiAssignmentsAsAutoAssignedUnlocked`: capture `saveAll` argument; assert each row has `assignedSource AUTO`, `assignmentStatus ASSIGNED`, `locked false`, and result `inserted` count matches.

`wrapsUniqueViolationAsApplyException`: stub `saveAll` to throw `DataIntegrityViolationException("dup")`; assert `AiApplyException`.

- [ ] **Step 6: Run all persistence tests and fix service**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiAssignmentPersistenceServiceTest test`

Expected: PASS (11 tests).

- [ ] **Step 7: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai/AiApplyException.java backend/src/main/java/scheduling/scheduling/ai/ApplyResult.java backend/src/main/java/scheduling/scheduling/ai/AiAssignmentPersistenceService.java backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceServiceTest.java && git commit -m "feat: apply AI assignments transactionally"`

## Task 3: Service Orchestration With TDD

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`

- [ ] **Step 1: Update test fixture for the new dependency**

The service constructor gains `AiAssignmentPersistenceService`. In `AdminOptimizationServiceTest.java` update the `new AdminOptimizationService(...)` construction (line ~54) to add `mock(AiAssignmentPersistenceService.class)` in the matching position, and declare the mock field. Read the current constructor call first and mirror its argument order.

- [ ] **Step 2: Write failing SUCCESS-persistence test**

Test name: `optimizePersistsAssignmentsBeforeFinishingSuccess()`.

Stub the existing SUCCESS fixture plus: `persistence.apply(eq(period), any(), eq(aiResponse))` returns `new ApplyResult(2, 1)`. Assert response `aiStatus=SUCCESS` and `InOrder`: `prepareSnapshot → weightsSnapshotFrom → start → buildFrom → optimize → persistence.apply → finishSuccess`, with `never().finishFailure(...)`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationServiceTest#optimizePersistsAssignmentsBeforeFinishingSuccess test`

Expected: FAIL (service does not call persistence yet; verify with `Wanted but not invoked`).

- [ ] **Step 3: Implement service changes**

In `AdminOptimizationService.java`:
- Add field + constructor parameter `AiAssignmentPersistenceService assignmentPersistenceService` (last position, matching test).
- Change `AiSchedulingResponseDto response;` before the `try` block to `AiSchedulingResponseDto response = null;` so the apply-failure catch can pass the AI response to `finishFailure` without a definite-assignment compile error.
- In `validateProtocol`, add after the existing list-null checks:
  - if `"SUCCESS".equals(response.status())`: reject null items, null `employeeId`/`workShiftId`, duplicate pairs → return `"null assignment item"`, `"assignment has null ids"`, `"duplicate assignment pair"` respectively.
  - if not SUCCESS and `!response.assignments().isEmpty()` → return `"assignments present for non-SUCCESS"`.
- In the SUCCESS branch, between protocol pass and `finishSuccess`, call:
  ```java
  assignmentPersistenceService.apply(period, snapshot, response);
  ```
  The `snapshot` variable is the `PayloadSnapshot` from `prepareSnapshot` (read the current method to get its exact variable name).
- Add catch before the generic `RuntimeException` safety net:
  ```java
  } catch (AiApplyException ex) {
      optimizationRunRecorder.finishFailure(
              run.getId(), "FAILED", "Failed to apply AI assignments: " + ex.getMessage(), response);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
  }
  ```
  Import `scheduling.scheduling.ai.AiApplyException`. The existing generic safety net stays as the last catch for non-apply unexpected failures; apply-service unexpected failures must be wrapped as `AiApplyException` by the persistence service so they use this lifecycle.

- [ ] **Step 4: Update existing SUCCESS tests to stub persistence**

Every existing test that reaches the SUCCESS branch will now hit the real (mocked-null) persistence call and NPE. Find all tests stubbing a SUCCESS AI response and add:
```java
when(assignmentPersistenceService.apply(eq(period), any(), eq(aiResponse))).thenReturn(new ApplyResult(1, 0));
```
Import `scheduling.scheduling.ai.ApplyResult`. Keep all other assertions unchanged.

- [ ] **Step 5: Add failing persistence-failure and protocol tests**

```java
@Test void optimizeRecordsFailedRunWhenPersistenceFails()
@Test void rejectsNonSuccessWithAssignmentsAsInvalidProtocol()
@Test void rejectsNullAssignmentItemAsInvalidProtocol()
@Test void rejectsDuplicateAssignmentPairAsInvalidProtocol()
```

`optimizeRecordsFailedRunWhenPersistenceFails`: SUCCESS response; stub `persistence.apply` to throw `new AiApplyException("AI assignments exceed required staff for shift 100")`; assert `BAD_GATEWAY`, verify `finishFailure(900L, "FAILED", contains "Failed to apply AI assignments", response-or-null)` and `never().finishSuccess(...)`. (Pass the AI response to finishFailure per the catch block above.)

Protocol tests: each asserts `BAD_GATEWAY`, `finishFailure` with reason containing `"Invalid AI response"`, and `verifyNoInteractions(assignmentPersistenceService)` (persistence never reached).

- [ ] **Step 6: Run service tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationServiceTest test`

Expected: PASS (existing 11 + 5 new = 16).

- [ ] **Step 7: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java && git commit -m "feat: persist AI assignments on success"`

## Task 4: Real Rollback Transaction Test

**Files:**
- Create: `backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceTransactionTest.java`

- [ ] **Step 1: Ensure the test database is running**

Run: `docker ps --filter name=scheduling-pg-test`

Expected: a running Postgres 16 container. If missing, start it with the credentials from `backend/src/main/resources/application.properties` defaults (db `scheduling_platform`, user `scheduling_platform`, password `scheduling_platform_dev_password`). Tests pin `user.timezone=UTC` via surefire, so no extra flag is needed.

- [ ] **Step 2: Write the rollback test**

Create a real `@SpringBootTest` (no mocks) following `OptimizationRunRecorderTransactionTest` structure. Test name: `failedApplyRollsBackAssignmentChanges()`.

Setup with real repositories: manager account + employee, DRAFT period (far-future dates to avoid collisions), shift type, one work shift (requiredStaff 1), one persisted unlocked AUTO assignment for that shift, one MANUAL assignment for another shift. Build a `PayloadSnapshot` via the real `AiSchedulingPayloadBuilder.prepareSnapshot` is heavy; instead construct the service inputs from real entities: call `persistence.apply(period, snapshot, response)` where `snapshot` comes from `payloadBuilder.prepareSnapshot(period, null)` with full rule seeding — check how `OptimizationRunRecorderTransactionTest` builds its fixtures and whether scheduling rules exist in the test DB (seeded by application startup or must be created; create the 11 required rules explicitly if absent).

AI response: valid SUCCESS shape but with an assignment for an UNKNOWN employee id (e.g. 999999L) so `apply` throws `AiApplyException` AFTER the unlocked AUTO delete.

Assertions after catching `AiApplyException`:
- the original unlocked AUTO row still exists (`shiftAssignmentRepository.findById` present, or `listForPeriod` contains it)
- no new AI rows exist (count of AUTO assignments for the period unchanged)
- the MANUAL row untouched

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiAssignmentPersistenceTransactionTest test`

Expected: PASS (proves delete + insert is all-or-nothing; the service-level 502 + run FAILED path is already covered by mocked tests in Task 3 and the recorder test from Slice 6A).

- [ ] **Step 3: Commit**

Run: `git add backend/src/test/java/scheduling/scheduling/ai/AiAssignmentPersistenceTransactionTest.java && git commit -m "test: prove assignment apply rolls back"`

## Task 5: Project Plan and Full Verification

**Files:**
- Modify: `docs/project_plan.md`
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` only if needed

- [ ] **Step 1: Run context test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=SchedulingApplicationTests test`

Expected: PASS. If it fails because the new `AiAssignmentPersistenceService` bean needs a repo mock, add the minimal `@MockitoBean` entry following the existing pattern (check whether `ShiftAssignmentRepository` is already mocked there first).

- [ ] **Step 2: Update project plan**

Tick only these Phase 6 items (find exact lines first):

- `AI service nhan danh sach nhan vien active`
- `AI service nhan shift types active`
- `AI service nhan approved leave requests`
- `AI service nhan preferences`
- `AI service nhan locked assignments`
- `Backend luu ShiftAssignment`
- `Backend luu OptimizationRun`

Do not tick frontend/retry/AI-pytest/deployment items. Verify with `git diff docs/project_plan.md` that only these lines flipped.

- [ ] **Step 3: Commit**

Run: `git add backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java docs/project_plan.md && git commit -m "docs: mark AI assignment persistence progress"`

If `SchedulingApplicationTests.java` did not change, run: `git add docs/project_plan.md && git commit -m "docs: mark AI assignment persistence progress"`.

- [ ] **Step 4: Full verification**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test`

Expected: all tests pass (plain command; UTC is pinned in surefire).

Run: `git diff --check`

Expected: no whitespace errors.

Run: `git status --short --branch`

Expected: branch `phase6/ai-assignment-persistence`; no tracked modifications. Existing unrelated untracked docs may remain:
```text
?? docs/quy_dinh_ca_lam_viec.md
?? docs/Đặc tả_Phát.txt
```

- [ ] **Step 5: Final commit only if verification fixes were needed**

If verification required code fixes, commit them with a focused message. Otherwise do not create an empty commit.

## Self-Review Notes

- Spec coverage: fixed-inputs payload (§3.2 solver rule) in Task 1; hard-invariant apply validation (§3.3) in Task 2; protocol tightening (§3.4) in Task 3; transaction/rollback + 502 lifecycle (§3.5) in Tasks 2-4; unchanged response DTO (§3.6); repository strategy (§5, load + deleteAll, no bulk modifying query); plan ticks (§6); testing plan (§7) including real rollback test.
- Placeholder scan: no `TBD`, `TODO`, or vague steps; edge cases named per task.
- Type consistency: `PayloadSnapshot.fixedAssignments`, `findFixedForPeriod`, `ApplyResult(inserted, skipped)`, `AiApplyException`, `ApplyResult` used consistently across tasks.

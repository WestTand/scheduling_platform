# Scheduling Accuracy Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist exact `WorkShift` time windows derived from `ShiftType` so overnight shifts have concrete start/end datetimes and downstream schedule views can use consistent window fields.

**Architecture:** Add nullable window columns to `WorkShift`, then centralize derivation/fallback logic in a small `WorkShiftWindowService`. `AdminWorkShiftService` uses the helper before saving new rows, and response DTOs use the helper to expose exact window fields while preserving existing local-time fields. This slice is backend-only: no frontend changes, no AI schema changes, no manual window override, and no migration framework.

**Tech Stack:** Java 17, Spring Boot WebMVC/Data JPA, JUnit 5, AssertJ, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-18-scheduling-accuracy-windows-design.md`

**Working directory:** All commands run from repo root (`scheduling_platform/`). Maven commands use `.\backend\mvnw.cmd -f backend/pom.xml`. In PowerShell, quote every `-D` argument, e.g. `"-Dtest=WorkShiftWindowServiceTest"`.

---

## File Map

- Modify: `backend/src/main/java/scheduling/scheduling/entity/WorkShift.java` — add nullable persisted window fields.
- Create: `backend/src/main/java/scheduling/scheduling/admin/WorkShiftWindowService.java` — derive and resolve work-shift windows.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java` — derive window on create.
- Modify DTOs: `AdminWorkShiftResponse`, `AdminPeriodScheduleResponse`, `EmployeeScheduleItemResponse` — expose exact window fields with fallback.
- Modify services/tests: work-shift service/controller, shift-assignment schedule views, employee schedule view.
- Modify docs: `docs/database-design-v2.md`, `docs/backend-ai-scheduling-contract.md`.

---

### Task 1: WorkShift Window Model And Derivation Helper

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/WorkShift.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/WorkShiftWindowService.java`
- Create test: `backend/src/test/java/scheduling/scheduling/admin/WorkShiftWindowServiceTest.java`
- Modify test if needed: `backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java`

- [ ] **Step 1: Write failing window helper tests**

Create `WorkShiftWindowServiceTest` with tests for:

- same-day `08:00-16:00`, `8.0h` on `2026-09-14` derives `2026-09-14T08:00+07:00`, `2026-09-14T16:00+07:00`, `480`, `false`;
- overnight `17:00-00:30`, `7.5h` derives end `2026-09-15T00:30+07:00`, `450`, `true`;
- end exactly midnight `16:00-00:00`, `8.0h` derives end `2026-09-15T00:00+07:00`, `480`, `true`;
- early morning `00:30-08:00`, `7.5h` derives same-day end, `450`, `false`;
- persisted complete window is returned as-is;
- null persisted field causes the full window to be computed from `workDate + shiftType`, not mixed.

Use these exact assertions in the same-day derivation test:

```java
assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
assertThat(window.workMinutes()).isEqualTo(480);
assertThat(window.transitionShift()).isFalse();
```

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=WorkShiftWindowServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: FAIL because `WorkShiftWindowService` and entity fields do not exist.

- [ ] **Step 2: Implement `WorkShift` fields**

Add to `WorkShift`:

```java
@Column(name = "starts_at")
private OffsetDateTime startsAt;

@Column(name = "ends_at")
private OffsetDateTime endsAt;

@Column(name = "work_minutes")
private Integer workMinutes;

@Column(name = "is_transition_shift")
private Boolean transitionShift;
```

Use Java field name `transitionShift` so Lombok accessors are `getTransitionShift()` / `setTransitionShift(...)`. Keep columns nullable for `ddl-auto=update` safety.

- [ ] **Step 3: Implement `WorkShiftWindowService`**

Create `backend/src/main/java/scheduling/scheduling/admin/WorkShiftWindowService.java`:

```java
package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;

public class WorkShiftWindowService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record Window(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer workMinutes,
            Boolean transitionShift) {
    }

    public Window derive(LocalDate workDate, ShiftType shiftType) {
        OffsetDateTime startsAt = workDate.atTime(shiftType.getStartTime()).atZone(ZONE).toOffsetDateTime();
        OffsetDateTime endsAt = workDate.atTime(shiftType.getEndTime()).atZone(ZONE).toOffsetDateTime();
        if (!endsAt.isAfter(startsAt)) {
            endsAt = endsAt.plusDays(1);
        }
        int workMinutes = shiftType.getDurationHours()
                .multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return new Window(startsAt, endsAt, workMinutes, endsAt.toLocalDate().isAfter(startsAt.toLocalDate()));
    }

    public Window resolve(WorkShift workShift) {
        if (workShift.getStartsAt() != null
                && workShift.getEndsAt() != null
                && workShift.getWorkMinutes() != null
                && workShift.getTransitionShift() != null) {
            return new Window(
                    workShift.getStartsAt(),
                    workShift.getEndsAt(),
                    workShift.getWorkMinutes(),
                    workShift.getTransitionShift());
        }
        return derive(workShift.getWorkDate(), workShift.getShiftType());
    }
}
```

Do not annotate as `@Service` unless constructor injection is needed later. Static utility is not required; instantiate where DTO mapping needs it.

- [ ] **Step 4: Run task tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=WorkShiftWindowServiceTest,EntityMappingTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/entity/WorkShift.java backend/src/main/java/scheduling/scheduling/admin/WorkShiftWindowService.java backend/src/test/java/scheduling/scheduling/admin/WorkShiftWindowServiceTest.java backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java
git add backend/src/main/java/scheduling/scheduling/entity/WorkShift.java backend/src/main/java/scheduling/scheduling/admin/WorkShiftWindowService.java backend/src/test/java/scheduling/scheduling/admin/WorkShiftWindowServiceTest.java backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java
git commit -m "feat: derive work shift windows"
```

---

### Task 2: Persist Windows On Work Shift Create And Admin WorkShift Responses

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java`

- [ ] **Step 1: Write failing service/controller tests**

Extend `AdminWorkShiftServiceTest`:

- in `createWorkShiftCreatesShiftInDraftPeriod`, capture the saved `WorkShift` and assert persisted fields:

```java
ArgumentCaptor<WorkShift> captor = ArgumentCaptor.forClass(WorkShift.class);
verify(workShiftRepository).save(captor.capture());
WorkShift saved = captor.getValue();
assertThat(saved.getStartsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
assertThat(saved.getEndsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
assertThat(saved.getWorkMinutes()).isEqualTo(480);
assertThat(saved.getTransitionShift()).isFalse();
assertThat(response.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
```

- add `responseFallsBackToDerivedWindowWhenPersistedFieldsAreNull` using existing `workShift()` helper and `AdminWorkShiftResponse.from(workShift())`, asserting derived values.
- add `responseUsesPersistedWindowWhenAllFieldsPresent` by setting custom persisted window values and asserting response returns those exact values.

Extend `AdminWorkShiftControllerTest` helper `response()` to include new constructor fields, and assert JSON shape:

```java
.andExpect(jsonPath("$[0].startsAt").value("2026-09-14T08:00:00+07:00"))
.andExpect(jsonPath("$[0].endsAt").value("2026-09-14T16:00:00+07:00"))
.andExpect(jsonPath("$[0].workMinutes").value(480))
.andExpect(jsonPath("$[0].isTransitionShift").value(false));
```

Update the `response()` helper constructor call to this exact argument order:

```java
return new AdminWorkShiftResponse(
        200L,
        20L,
        "Week 38",
        100L,
        "Morning",
        LocalTime.of(8, 0),
        LocalTime.of(16, 0),
        LocalDate.of(2026, 9, 14),
        OffsetDateTime.parse("2026-09-14T08:00:00+07:00"),
        OffsetDateTime.parse("2026-09-14T16:00:00+07:00"),
        480,
        false,
        2,
        "Morning coverage");
```

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest,AdminWorkShiftControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: FAIL because `AdminWorkShiftResponse` lacks new fields and create does not persist windows.

- [ ] **Step 2: Implement service create persistence**

In `AdminWorkShiftService.createWorkShift`, after `note` is normalized and before builder creation, derive window:

```java
WorkShiftWindowService.Window window = new WorkShiftWindowService().derive(request.workDate(), shiftType);
```

Add these to the `WorkShift.builder()` call:

```java
.startsAt(window.startsAt())
.endsAt(window.endsAt())
.workMinutes(window.workMinutes())
.transitionShift(window.transitionShift())
```

Keep request DTO unchanged.

- [ ] **Step 3: Update `AdminWorkShiftResponse`**

Add imports and fields:

```java
import java.time.OffsetDateTime;
import scheduling.scheduling.admin.WorkShiftWindowService;
```

Add fields after `shiftEndTime` or after `workDate` consistently:

```java
OffsetDateTime startsAt,
OffsetDateTime endsAt,
Integer workMinutes,
Boolean isTransitionShift,
```

In `from(WorkShift workShift)`, resolve full window:

```java
WorkShiftWindowService.Window window = new WorkShiftWindowService().resolve(workShift);
```

Use `window.startsAt()`, `window.endsAt()`, `window.workMinutes()`, `window.transitionShift()` in the record constructor.

- [ ] **Step 4: Run task tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest,AdminWorkShiftControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java
git add backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java
git commit -m "feat: persist work shift windows"
```

---

### Task 3: Manager And Employee Schedule Response Windows

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java`

- [ ] **Step 1: Write failing schedule response tests**

Extend `AdminShiftAssignmentServiceTest.scheduleViewReturnsOrderedShiftsWithAssignments` to assert exact window fields on `response.workShifts().get(0)` and `get(1)`:

```java
assertThat(response.workShifts().get(0).startsAt())
        .isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
assertThat(response.workShifts().get(0).endsAt())
        .isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
assertThat(response.workShifts().get(0).workMinutes()).isEqualTo(480);
assertThat(response.workShifts().get(0).isTransitionShift()).isFalse();
```

Add or update an employee schedule test in `EmployeeScheduleServiceTest` to assert `startsAt`, `endsAt`, `workMinutes`, `isTransitionShift` on returned `EmployeeScheduleItemResponse`.

Update `EmployeeScheduleControllerTest.response()` to use the new constructor shape and add JSON assertions to `employeeViewsOwnSchedule`:

```java
.andExpect(jsonPath("$[0].startsAt").value("2026-09-17T08:00:00+07:00"))
.andExpect(jsonPath("$[0].endsAt").value("2026-09-17T16:00:00+07:00"))
.andExpect(jsonPath("$[0].workMinutes").value(480))
.andExpect(jsonPath("$[0].isTransitionShift").value(false));
```

Use this exact updated helper return value:

```java
return new EmployeeScheduleItemResponse(
        300L,
        "ASSIGNED",
        "MANUAL",
        false,
        LocalDate.of(2026, 9, 17),
        100L,
        "Day",
        LocalTime.of(8, 0),
        LocalTime.of(16, 0),
        OffsetDateTime.parse("2026-09-17T08:00:00+07:00"),
        OffsetDateTime.parse("2026-09-17T16:00:00+07:00"),
        480,
        false,
        20L,
        "Week 38",
        "PUBLISHED");
```

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentServiceTest,EmployeeScheduleServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: FAIL because response DTOs lack new fields.

- [ ] **Step 2: Update `AdminPeriodScheduleResponse.ScheduledShift`**

Add imports/fields:

```java
import java.time.OffsetDateTime;
```

Add to `ScheduledShift`:

```java
OffsetDateTime startsAt,
OffsetDateTime endsAt,
Integer workMinutes,
Boolean isTransitionShift,
```

Keep existing `shiftStartTime`, `shiftEndTime`, and `overnight` fields for compatibility.

- [ ] **Step 3: Update `AdminShiftAssignmentService.getSchedule` mapping**

Before constructing `ScheduledShift`, resolve window:

```java
WorkShiftWindowService windowService = new WorkShiftWindowService();
```

Inside `.map(...)` block, use a block lambda:

```java
.map(shift -> {
    WorkShiftWindowService.Window window = windowService.resolve(shift);
    return new AdminPeriodScheduleResponse.ScheduledShift(
            shift.getId(),
            shift.getWorkDate(),
            shift.getShiftType().getId(),
            shift.getShiftType().getShiftName(),
            shift.getShiftType().getStartTime(),
            shift.getShiftType().getEndTime(),
            window.startsAt(),
            window.endsAt(),
            window.workMinutes(),
            window.transitionShift(),
            shift.getShiftType().getOvernight(),
            shift.getRequiredStaff(),
            shift.getShiftType().getMaxStaff(),
            byShift.getOrDefault(shift.getId(), List.of()));
})
```

Add import `scheduling.scheduling.admin.WorkShiftWindowService` only if needed by package context; same package means no import required.

- [ ] **Step 4: Update `EmployeeScheduleItemResponse`**

Add fields:

```java
OffsetDateTime startsAt,
OffsetDateTime endsAt,
Integer workMinutes,
Boolean isTransitionShift,
```

In `from(ShiftAssignment assignment)`, resolve window from `assignment.getWorkShift()` and pass fields in this order:

```java
WorkShiftWindowService.Window window = new WorkShiftWindowService().resolve(assignment.getWorkShift());
return new EmployeeScheduleItemResponse(
        assignment.getId(),
        assignment.getAssignmentStatus(),
        assignment.getAssignedSource(),
        assignment.getLocked(),
        assignment.getWorkShift().getWorkDate(),
        assignment.getWorkShift().getShiftType().getId(),
        assignment.getWorkShift().getShiftType().getShiftName(),
        assignment.getWorkShift().getShiftType().getStartTime(),
        assignment.getWorkShift().getShiftType().getEndTime(),
        window.startsAt(),
        window.endsAt(),
        window.workMinutes(),
        window.transitionShift(),
        assignment.getWorkShift().getSchedulePeriod().getId(),
        assignment.getWorkShift().getSchedulePeriod().getPeriodName(),
        assignment.getWorkShift().getSchedulePeriod().getStatus());
```

- [ ] **Step 5: Run task tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentServiceTest,EmployeeScheduleServiceTest,EmployeeScheduleControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java
git add backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java
git commit -m "feat: expose schedule window fields"
```

---

### Task 4: Documentation And Full Verification

**Files:**
- Modify: `docs/database-design-v2.md`
- Modify: `docs/backend-ai-scheduling-contract.md`

- [ ] **Step 1: Update database docs**

In `docs/database-design-v2.md` section `3.5 work_shifts`, add:

```markdown
| starts_at | TIMESTAMP WITH TIME ZONE | NULL in current ddl-auto slice | Derived from work_date + shift_type.start_time using Asia/Ho_Chi_Minh |
| ends_at | TIMESTAMP WITH TIME ZONE | NULL in current ddl-auto slice | Add one day when derived end is not after start |
| work_minutes | INT | NULL in current ddl-auto slice | Derived from shift_type.duration_hours * 60 |
| is_transition_shift | BOOLEAN | NULL in current ddl-auto slice | True when ends_at local date is after starts_at local date |
```

Add a short note: new rows persist these fields; old rows may be null until a future migration/backfill.

- [ ] **Step 2: Update AI contract docs**

In `docs/backend-ai-scheduling-contract.md`, update the `workShifts` section to state:

- backend now persists exact `startsAt` and `endsAt` on `work_shifts`;
- this slice does not change `schemaVersion` from `1.0`;
- a later AI payload slice can switch snapshot assembly to persisted windows if it is not already doing so.

Do not document new AI request fields beyond the existing `startsAt`/`endsAt` examples.

- [ ] **Step 3: Full verification**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean test
```

Expected: all backend tests PASS.

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` has no output. `git status --short` shows only intentional docs changes and the known unrelated untracked external docs, if still present:

```text
?? docs/quy_dinh_ca_lam_viec.md
?? "docs/Đặc tả_Phát.txt"
```

Do not stage unrelated untracked external source docs.

- [ ] **Step 4: Commit docs**

```bash
git diff -- docs/database-design-v2.md docs/backend-ai-scheduling-contract.md
git add docs/database-design-v2.md docs/backend-ai-scheduling-contract.md
git commit -m "docs: update work shift window model"
```

---

## Spec Coverage

- WorkShift nullable window fields: Task 1.
- `OffsetDateTime` + `Asia/Ho_Chi_Minh` derivation: Task 1.
- Derived-only create behavior: Task 2.
- Admin work-shift response windows and fallback semantics: Task 2.
- Manager period schedule + employee schedule response windows: Task 3.
- No AI schema/front-end/manual override/migration/backfill changes: all tasks explicitly avoid them.
- Docs and full backend verification: Task 4.

## Self-Review

- Spec coverage: every in-scope item maps to a task.
- Placeholder scan: no TBD/TODO placeholders or vague implementation steps remain.
- Type consistency: `startsAt`, `endsAt`, `workMinutes`, `transitionShift`, and `isTransitionShift` are used consistently. Java entity field uses `transitionShift`; JSON/record accessor exposes `isTransitionShift` via record component naming.
- Scope check: plan is a single backend-only slice and intentionally defers AI, frontend, migration, and backfill work.

# Schedule Pool Scoping And Staffing Advice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI Optimize always respect the schedule period's employee pool, allow editing that pool after creation, and show the minimum number of employees required when a schedule is INFEASIBLE.

**Architecture:** The AI service computes a staffing lower bound (it owns the rule semantics) and returns it in the optimize response. The backend persists that advice on the optimization run and exposes it on the period schedule endpoint. The backend scopes optimize to the period pool and gains an endpoint to replace the pool. The frontend maps the advice to Vietnamese copy and adds a pool editor dialog.

**Tech Stack:** Python 3 + FastAPI + OR-Tools CP-SAT (ai-service), Java 21 + Spring Boot 4 + JPA/Postgres (backend), React 19 + TypeScript + Vite + Vitest (frontend).

---

## Task 1: AI service staffing lower bound

**Files:**
- Create: `ai-service/app/solver/staffing.py`
- Modify: `ai-service/app/schemas/scheduling.py`
- Modify: `ai-service/app/solver/scheduler.py`
- Test: `ai-service/tests/test_staffing.py`

- [ ] **Step 1: Write the failing test** in `ai-service/tests/test_staffing.py`:

```python
from datetime import date, datetime, time, timedelta, timezone

from app.schemas.scheduling import SchedulingRequest
from app.solver.staffing import estimate_required_employees
from app.solver.scheduler import InvalidSchedulingInput

TZ = timezone(timedelta(hours=7))


def _dt(day: date, hhmm: str) -> datetime:
    hour, minute = (int(part) for part in hhmm.split(":"))
    return datetime.combine(day, time(hour, minute), tzinfo=TZ)


def _request(shifts, employees, rules) -> SchedulingRequest:
    shift_types = [
        {
            "shiftTypeId": shift["shiftTypeId"],
            "durationMinutes": shift["durationMinutes"],
            "overnight": False,
            "heavy": False,
            "minStaff": 1,
            "maxStaff": 20,
            "quotaCategory": shift["quotaCategory"],
        }
        for shift in shifts
    ]
    work_shifts = [
        {
            "workShiftId": shift["id"],
            "shiftTypeId": shift["shiftTypeId"],
            "workDate": shift["date"].isoformat(),
            "startsAt": _dt(shift["date"], shift["start"]).isoformat(),
            "endsAt": _dt(shift["date"], shift["end"]).isoformat(),
            "requiredStaff": 1,
        }
        for shift in shifts
    ]
    return SchedulingRequest.model_validate(
        {
            "schemaVersion": "1.0",
            "runId": 1,
            "period": {
                "periodId": 1,
                "startDate": min(shift["date"] for shift in shifts).isoformat(),
                "endDate": max(shift["date"] for shift in shifts).isoformat(),
                "versionNo": 1,
                "timezone": "Asia/Ho_Chi_Minh",
            },
            "employees": [{"employeeId": employee_id} for employee_id in employees],
            "shiftTypes": shift_types,
            "workShifts": work_shifts,
            "approvedLeaves": [],
            "preferences": [],
            "lockedAssignments": [],
            "boundaryAssignments": [],
            "rules": rules,
            "solverOptions": {"timeLimitSeconds": 5, "randomSeed": 1, "numWorkers": 1},
        }
    )


DEFAULT_RULES = {
    "hardRules": {
        "minRestHoursBetweenShifts": 12,
        "maxWorkHoursPerDay": 12,
        "maxShiftsPerWeek": 6,
    },
    "quotas": {
        "overtimeShiftsPerWeek": 1,
        "bathingShiftsPerWeek": 2,
        "onCallEveryNDays": 6,
    },
    "softWeights": {
        "fairness": 10,
        "preference": 5,
        "weekend": 5,
        "heavyShift": 8,
        "workHourBalance": 10,
    },
}


def _week_with_three_shifts_per_day() -> list[dict]:
    shifts = []
    shift_id = 1
    day = date(2026, 9, 28)
    for _ in range(5):
        for start, end, code, quota in (
            ("08:00", "17:00", 1, "BATHING"),
            ("17:00", "00:30", 2, "OVERTIME"),
            ("00:30", "08:00", 3, "OVERTIME"),
        ):
            shifts.append(
                {
                    "id": shift_id,
                    "shiftTypeId": code,
                    "durationMinutes": 450,
                    "date": day,
                    "start": start,
                    "end": end,
                    "quotaCategory": quota,
                }
            )
            shift_id += 1
        day += timedelta(days=1)
    return shifts


def test_overtime_quota_forces_one_employee_per_overtime_date():
    request = _request(_week_with_three_shifts_per_day(), [1], DEFAULT_RULES)

    advice = estimate_required_employees(request)

    assert advice.available_employees == 1
    assert advice.required_employees == 10
    codes = {reason.code for reason in advice.reasons if reason.required_employees == advice.required_employees}
    assert "QUOTA_OVERTIME" in codes


def test_weekly_capacity_is_used_when_no_quota_binds():
    shifts = [
        {
            "id": index,
            "shiftTypeId": 4,
            "durationMinutes": 480,
            "date": date(2026, 9, 28) + timedelta(days=index),
            "start": "08:00",
            "end": "16:00",
            "quotaCategory": None,
        }
        for index in range(1, 8)
    ]
    request = _request(shifts, [1, 2, 3], DEFAULT_RULES)

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 7
    assert advice.required_employees == 2
    assert "WEEKLY_CAPACITY" in {reason.code for reason in advice.reasons}


def test_on_call_gap_reuses_employees_far_apart_in_time():
    shifts = [
        {
            "id": 1,
            "shiftTypeId": 4,
            "durationMinutes": 480,
            "date": date(2026, 10, 3),
            "start": "08:00",
            "end": "16:00",
            "quotaCategory": "ON_CALL",
        },
        {
            "id": 2,
            "shiftTypeId": 4,
            "durationMinutes": 480,
            "date": date(2026, 10, 10),
            "start": "08:00",
            "end": "16:00",
            "quotaCategory": "ON_CALL",
        },
    ]
    request = _request(shifts, [1, 2], DEFAULT_RULES)

    advice = estimate_required_employees(request)

    assert advice.required_employees == 1
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.venv\Scripts\pytest.exe -q tests/test_staffing.py`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.solver.staffing'`

- [ ] **Step 3: Add the response schema** in `ai-service/app/schemas/scheduling.py`, appended at the end of the file:

```python
class StaffingReason(BaseModel):
    code: str
    required_employees: int
    detail: str


class StaffingAdvice(BaseModel):
    required_employees: int
    available_employees: int
    total_shifts: int
    reasons: list[StaffingReason] = Field(default_factory=list)
```

And add the field to `SchedulingResponse` (insert after `diagnostics`):

```python
    staffing_advice: StaffingAdvice | None = None
```

- [ ] **Step 4: Implement the estimator** in `ai-service/app/solver/staffing.py`:

```python
from __future__ import annotations

import math
from collections import defaultdict
from datetime import timedelta

from app.schemas.scheduling import SchedulingRequest, StaffingAdvice, StaffingReason


def _ceil_div(total: int, per_employee: int) -> int:
    if per_employee <= 0:
        return total
    return math.ceil(total / per_employee)


def _on_call_max_per_employee(dates: list, gap_days: int) -> int:
    ordered = sorted(set(dates))
    if not ordered:
        return 0
    best = 0
    start = 0
    for end in range(len(ordered)):
        while (ordered[end] - ordered[start]).days >= gap_days:
            start += 1
        best = max(best, end - start + 1)
    return best


def _same_day_rest_bound(shifts, rest_minutes: int) -> int:
    by_date = defaultdict(list)
    for shift in shifts:
        by_date[shift.work_date].append(shift)
    bound = 0
    for day_shifts in by_date.values():
        events = []
        for shift in day_shifts:
            rest = timedelta(minutes=rest_minutes)
            events.append((shift.starts_at - rest, 1))
            events.append((shift.ends_at + rest, -1))
        events.sort(key=lambda item: (item[0], -item[1]))
        current = 0
        for _, delta in events:
            current += delta
            bound = max(bound, current)
    return bound


def estimate_required_employees(request: SchedulingRequest) -> StaffingAdvice:
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}
    shifts = request.work_shifts
    total_shifts = len(shifts)
    available = len(request.employees)
    rest_minutes = request.rules.hard_rules.min_rest_hours_between_shifts * 60
    quotas = request.rules.quotas
    hard_rules = request.rules.hard_rules

    reasons: list[StaffingReason] = []
    if hard_rules.max_shifts_per_week > 0:
        reasons.append(
            StaffingReason(
                code="WEEKLY_CAPACITY",
                required_employees=_ceil_div(total_shifts, hard_rules.max_shifts_per_week),
                detail=f"Each employee may take at most {hard_rules.max_shifts_per_week} shifts per week.",
            )
        )

    quota_limits = {
        "OVERTIME": quotas.overtime_shifts_per_week,
        "BATHING": quotas.bathing_shifts_per_week,
    }
    for category, limit in quota_limits.items():
        if limit <= 0:
            continue
        dates = {
            shift.work_date
            for shift in shifts
            if shift_type_by_id[shift.shift_type_id].quota_category == category
        }
        reasons.append(
            StaffingReason(
                code=f"QUOTA_{category}",
                required_employees=_ceil_div(len(dates), limit),
                detail=f"Each employee may cover at most {limit} {category.lower()} day(s) per week.",
            )
        )

    on_call_dates = [
        shift.work_date
        for shift in shifts
        if shift_type_by_id[shift.shift_type_id].quota_category == "ON_CALL"
    ]
    on_call_per_employee = _on_call_max_per_employee(on_call_dates, quotas.on_call_every_n_days)
    if on_call_dates and on_call_per_employee > 0:
        reasons.append(
            StaffingReason(
                code="ON_CALL_GAP",
                required_employees=_ceil_div(len(on_call_dates), on_call_per_employee),
                detail=(
                    f"One employee may hold at most {on_call_per_employee} on-call shift(s) "
                    f"within any {quotas.on_call_every_n_days}-day window."
                ),
            )
        )

    rest_bound = _same_day_rest_bound(shifts, rest_minutes)
    if rest_bound > 0:
        reasons.append(
            StaffingReason(
                code="SAME_DAY_REST",
                required_employees=rest_bound,
                detail=(
                    f"Overlapping shifts on the same day need distinct employees because of the "
                    f"{request.rules.hard_rules.min_rest_hours_between_shifts}h minimum rest."
                ),
            )
        )

    required = max((reason.required_employees for reason in reasons), default=0)
    return StaffingAdvice(
        required_employees=required,
        available_employees=available,
        total_shifts=total_shifts,
        reasons=[reason for reason in reasons if reason.required_employees == required],
    )
```

- [ ] **Step 5: Wire the estimator into the solver** in `ai-service/app/solver/scheduler.py`.

Add the import at the top of the file:

```python
from app.solver.staffing import estimate_required_employees
```

In `solve_schedule`, immediately after `_validate_request(request)`:

```python
    staffing_advice = estimate_required_employees(request)
```

Add `staffing_advice=staffing_advice,` to the `SchedulingResponse(...)` call in the `OPTIMAL/FEASIBLE` branch, the `INFEASIBLE` branch, and the `FAILED` branch.

- [ ] **Step 6: Run the test to verify it passes**

Run: `.venv\Scripts\pytest.exe -q tests/test_staffing.py`
Expected: PASS

- [ ] **Step 7: Run the full ai-service suite**

Run: `.venv\Scripts\pytest.exe -q`
Expected: all tests pass

---

## Task 2: Backend persists and exposes staffing advice

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/OptimizationRun.java`
- Modify: `backend/src/main/java/scheduling/scheduling/ai/dto/AiSchedulingResponseDto.java`
- Modify: `backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
- Test: `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`

- [ ] **Step 1: Write the failing test** — add to `OptimizationRunRecorderTest`:

```java
    @Test
    void finishSuccessStoresStaffingAdvice() {
        SchedulePeriod period = SchedulePeriod.builder().periodName("Week").build();
        period.setId(20L);
        OptimizationRun run = OptimizationRun.builder()
                .schedulePeriod(period)
                .status("RUNNING")
                .startedAt(LocalDateTime.of(2026, 9, 20, 8, 0))
                .build();
        run.setId(42L);
        when(optimizationRunRepository.findById(42L)).thenReturn(Optional.of(run));
        when(optimizationRunRepository.save(any(OptimizationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.finishSuccess(42L, new AiSchedulingResponseDto(
                "1.0", 42L, 20L, "SUCCESS", "OPTIMAL", List.of(), BigDecimal.ZERO,
                Map.of(), List.of(), List.of(), null, null,
                new AiSchedulingResponseDto.StaffingAdviceDto(10, 4, 21, List.of("QUOTA_OVERTIME")),
                new AiSchedulingResponseDto.StatisticsDto(BigDecimal.ZERO, 0, 0)));

        assertThat(run.getStaffingAdviceJson()).contains("\"requiredEmployees\":10");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -q '-Dtest=OptimizationRunRecorderTest' test`
Expected: FAIL to compile because `StaffingAdviceDto` and `getStaffingAdviceJson` do not exist.

- [ ] **Step 3: Add the DTO types** in `AiSchedulingResponseDto.java`:

```java
    public record StaffingAdviceDto(
            Integer requiredEmployees,
            Integer availableEmployees,
            Integer totalShifts,
            List<String> reasons) {}
```

and add the record component after `errorMessage`:

```java
        StaffingAdviceDto staffingAdvice,
```

- [ ] **Step 4: Add the entity column** in `OptimizationRun.java`, after `penaltyDetailsJson`:

```java
    /** JSON snapshot of the AI staffing lower bound, used to explain INFEASIBLE results. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staffing_advice_json", columnDefinition = "JSONB")
    private String staffingAdviceJson;
```

- [ ] **Step 5: Persist it in the recorder** in `OptimizationRunRecorder.java`.

In `finishSuccess`, after `run.setPenaltyDetailsJson(...)`:

```java
        run.setStaffingAdviceJson(response.staffingAdvice() == null ? null : writeValue(response.staffingAdvice()));
```

In `finishFailure`, after the existing `setPenaltyDetailsJson`:

```java
        run.setStaffingAdviceJson(response == null || response.staffingAdvice() == null
                ? null
                : writeValue(response.staffingAdvice()));
```

- [ ] **Step 6: Expose it on the schedule response** in `AdminPeriodScheduleResponse.java`, inside `LatestOptimizationRun` after `diagnostics`:

```java
            StaffingAdvice staffingAdvice) {
    }

    public record StaffingAdvice(
            Integer requiredEmployees,
            Integer availableEmployees,
            Integer totalShifts,
            List<String> reasons) {
    }
```

- [ ] **Step 7: Parse it in the service** in `AdminShiftAssignmentService.java`.

Add a private method next to `parseDiagnostics`:

```java
    private AdminPeriodScheduleResponse.StaffingAdvice parseStaffingAdvice(OptimizationRun run) {
        if (run == null || run.getStaffingAdviceJson() == null || run.getStaffingAdviceJson().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(run.getStaffingAdviceJson());
            List<String> reasons = new ArrayList<>();
            JsonNode reasonArray = node.get("reasons");
            if (reasonArray != null && reasonArray.isArray()) {
                for (JsonNode reason : reasonArray) {
                    reasons.add(reason.asText());
                }
            }
            return new AdminPeriodScheduleResponse.StaffingAdvice(
                    node.path("requiredEmployees").isMissingNode() ? null : node.get("requiredEmployees").asInt(),
                    node.path("availableEmployees").isMissingNode() ? null : node.get("availableEmployees").asInt(),
                    node.path("totalShifts").isMissingNode() ? null : node.get("totalShifts").asInt(),
                    reasons);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
```

Wire it into the `latestOptimizationRun` construction by adding the argument, and add the required imports (`com.fasterxml.jackson.databind.JsonNode`, `java.util.ArrayList`) if absent.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./mvnw.cmd -q '-Dtest=OptimizationRunRecorderTest,AdminShiftAssignmentServiceTest' test`
Expected: PASS

---

## Task 3: Scope optimize to the period employee pool

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`

- [ ] **Step 1: Write the failing test** — add to `AdminOptimizationServiceTest`:

```java
    @Test
    void optimizeWithoutEmployeeIdsUsesThePeriodEmployeePool() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(managerAccount()));
        SchedulePeriod period = draftPeriodWithPool(31L, 32L);
        when(schedulePeriodRepository.findDetailById(7L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), any(), anyCollection()))
                .thenReturn(emptySnapshot());

        service.optimize(managerPrincipal(), 7L, new OptimizeScheduleRequest(null, null));

        verify(payloadBuilder).prepareSnapshot(eq(period), any(), eq(List.of(31L, 32L)));
    }
```

with helpers:

```java
    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Account managerAccount() {
        Account account = Account.builder()
                .username("manager01")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(1L);
        return account;
    }

    private static SchedulePeriod draftPeriodWithPool(Long... employeeIds) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .build();
        period.setId(7L);
        for (Long employeeId : employeeIds) {
            Employee employee = Employee.builder().employeeCode("EMP" + employeeId).fullName("Staff " + employeeId).status("ACTIVE").build();
            employee.setId(employeeId);
            period.getEmployeePool().add(employee);
        }
        return period;
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd -q '-Dtest=AdminOptimizationServiceTest' test`
Expected: FAIL because `prepareSnapshot(period, options)` is called instead of the pool-scoped overload.

- [ ] **Step 3: Implement the pool default** in `AdminOptimizationService.optimize`, replacing:

```java
        List<Long> employeeIds = request == null ? null : request.employeeIds();

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = employeeIds == null || employeeIds.isEmpty()
                ? payloadBuilder.prepareSnapshot(period, options)
                : payloadBuilder.prepareSnapshot(period, options, employeeIds);
```

with:

```java
        List<Long> employeeIds = request == null ? null : request.employeeIds();
        // Without an explicit selection, the period's own employee pool is the only allowed
        // candidate set. Falling back to every active employee would staff the schedule with
        // people the manager never selected for this period.
        List<Long> scopedEmployeeIds = employeeIds == null || employeeIds.isEmpty()
                ? periodPoolIds(period)
                : employeeIds;

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = scopedEmployeeIds.isEmpty()
                ? payloadBuilder.prepareSnapshot(period, options)
                : payloadBuilder.prepareSnapshot(period, options, scopedEmployeeIds);
```

and add the helper:

```java
    private static List<Long> periodPoolIds(SchedulePeriod period) {
        if (period.getEmployeePool() == null) {
            return List.of();
        }
        return period.getEmployeePool().stream()
                .map(Employee::getId)
                .filter(Objects::nonNull)
                .toList();
    }
```

Add `import java.util.Objects;` and `import scheduling.scheduling.entity.Employee;` if absent.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw.cmd -q '-Dtest=AdminOptimizationServiceTest' test`
Expected: PASS

---

## Task 4: Employee pool update endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateEmployeePoolRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`

- [ ] **Step 1: Write the failing service test** — add to `AdminSchedulePeriodServiceTest`:

```java
    @Test
    void updateEmployeePoolReplacesTheDraftPool() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(first, second));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.updateEmployeePool(principal, 20L, List.of(10L, 11L));

        assertThat(period.getEmployeePool()).extracting(Employee::getId).containsExactly(10L, 11L);
        assertThat(response.id()).isEqualTo(20L);
    }

    @Test
    void updateEmployeePoolRejectsPublishedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        SchedulePeriod published = draftPeriod(20L, account(principal));
        published.setStatus("PUBLISHED");
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateEmployeePoolRejectsUnknownEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod(20L, manager)));
        when(employeeRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(activeEmployee(10L, "EMP010", "Employee Ten")));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw.cmd -q '-Dtest=AdminSchedulePeriodServiceTest' test`
Expected: FAIL to compile because `updateEmployeePool` and `employeeRepository` do not exist.

- [ ] **Step 3: Create the request DTO** at `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateEmployeePoolRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateEmployeePoolRequest(@NotEmpty List<Long> employeeIds) {
}
```

- [ ] **Step 4: Implement the service method** in `AdminSchedulePeriodService`, near `cancelSchedulePeriod`:

```java
    @Transactional
    public AdminSchedulePeriodResponse updateEmployeePool(
            CurrentAccountPrincipal principal, Long periodId, List<Long> employeeIds) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách nhân viên không được để trống.");
        }
        List<Employee> employees = employeeRepository.findAllById(new LinkedHashSet<>(employeeIds));
        if (employees.size() != new LinkedHashSet<>(employeeIds).size()
                || employees.stream().anyMatch(employee -> !ACTIVE.equals(employee.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách nhân viên không hợp lệ.");
        }
        Map<Long, Employee> byId = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        period.setEmployeePool(employeeIds.stream()
                .map(byId::get)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return toResponse(savePeriod(period));
    }
```

Add fields/imports: `private final EmployeeRepository employeeRepository;` (plus constructor parameter and assignment), `import java.util.LinkedHashSet;`, `import java.util.function.Function;`, `import scheduling.scheduling.entity.Employee;`, `import scheduling.scheduling.repository.EmployeeRepository;`.

- [ ] **Step 5: Add the controller mapping** in `AdminSchedulePeriodController`, after `approveSchedulePeriod`:

```java
    @PutMapping("/{periodId}/employee-pool")
    ResponseEntity<AdminSchedulePeriodResponse> updateEmployeePool(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody UpdateEmployeePoolRequest request) {
        return ResponseEntity.ok(adminSchedulePeriodService.updateEmployeePool(principal, periodId, request.employeeIds()));
    }
```

Add `import scheduling.scheduling.admin.dto.UpdateEmployeePoolRequest;`.

- [ ] **Step 6: Add a controller test** to `AdminSchedulePeriodControllerTest` asserting `PUT /api/admin/schedule-periods/20/employee-pool` returns 200 and delegates.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw.cmd -q '-Dtest=AdminSchedulePeriodServiceTest,AdminSchedulePeriodControllerTest' test`
Expected: PASS

---

## Task 5: Frontend pool editing

**Files:**
- Create: `frontend/src/services/backendEmployeePoolService.ts`
- Modify: `frontend/src/types/backendSchedule.ts`
- Modify: `frontend/src/pages/manager/ScheduleDetailPage.tsx`
- Create: `frontend/src/components/schedule/ScheduleEmployeePoolEditor.tsx`
- Test: `frontend/src/pages/manager/ScheduleDetailPage.helpers.test.ts`

- [ ] **Step 1: Write the failing test** for the pool editor helpers:

```ts
import { describe, expect, it } from 'vitest'

import { buildEmployeePoolPayload, getEmployeePoolSelection } from '@/components/schedule/ScheduleEmployeePoolEditor.helpers'

const candidates = [
  { id: '1', fullName: 'Nguyễn Văn An', employeeCode: 'EMP001' },
  { id: '2', fullName: 'Trần Thị Bình', employeeCode: 'EMP002' },
]

describe('ScheduleEmployeePoolEditor helpers', () => {
  it('preselects the current pool members', () => {
    expect(getEmployeePoolSelection(candidates, ['2'])).toEqual(['2'])
  })

  it('rejects an empty selection', () => {
    expect(buildEmployeePoolPayload([])).toBe('')
    expect(buildEmployeePoolPayload(['1'])).toBe('[1]')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm.cmd test -- ScheduleEmployeePoolEditor.helpers`
Expected: FAIL because the helper module does not exist.

- [ ] **Step 3: Implement the helpers** in `frontend/src/components/schedule/ScheduleEmployeePoolEditor.helpers.ts`:

```ts
export interface PoolCandidate {
  id: string
  fullName: string
  employeeCode: string
}

export const getEmployeePoolSelection = (candidates: PoolCandidate[], currentPoolIds: string[]) => {
  const available = new Set(candidates.map((candidate) => candidate.id))
  return currentPoolIds.filter((id) => available.has(id))
}

export const buildEmployeePoolPayload = (selectedIds: string[]) =>
  selectedIds.length ? JSON.stringify(selectedIds.map(Number)) : ''
```

- [ ] **Step 4: Create the service** `frontend/src/services/backendEmployeePoolService.ts`:

```ts
import { adaptBackendSchedulePeriod } from '@/adapters/scheduleAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendSchedulePeriodResponse } from '@/types/backendSchedule'
import type { WorkSchedule } from '@/types/schedule'

export const backendEmployeePoolService = {
  async update(periodId: string, employeeIds: number[]): Promise<WorkSchedule> {
    const response = await requestJson<BackendSchedulePeriodResponse>(
      `/api/admin/schedule-periods/${encodeURIComponent(periodId)}/employee-pool`,
      { method: 'PUT', body: { employeeIds } },
    )
    return adaptBackendSchedulePeriod(response)
  },
}
```

- [ ] **Step 5: Add the editor component** `frontend/src/components/schedule/ScheduleEmployeePoolEditor.tsx` that renders a `ConfirmDialog` with checkboxes over the candidates, a select-all/deselect-all toggle, and a save handler calling `backendEmployeePoolService.update`.

- [ ] **Step 6: Wire it into `ScheduleDetailPage`**: add state `poolEditorOpen`, load active employees with `backendEmployeeService.list({ page: 1, pageSize: 500, status: 'ACTIVE' })`, render an "Chỉnh sửa pool nhân viên" button (only when `detail.status === 'DRAFT'`), and refresh the detail after saving.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `npm.cmd test -- ScheduleEmployeePoolEditor`
Expected: PASS

---

## Task 6: Frontend staffing advice banner

**Files:**
- Modify: `frontend/src/types/schedule.ts`
- Modify: `frontend/src/types/backendSchedule.ts`
- Modify: `frontend/src/adapters/scheduleAdapter.ts`
- Modify: `frontend/src/pages/manager/ScheduleDetailPage.tsx`
- Test: `frontend/src/pages/manager/ScheduleDetailPage.helpers.test.ts`
- Test: `frontend/src/adapters/scheduleAdapter.test.ts`

- [ ] **Step 1: Write the failing tests.**

In `ScheduleDetailPage.helpers.test.ts`:

```ts
  it('explains how many employees the infeasible schedule still needs', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', { requiredEmployees: 10, availableEmployees: 1, totalShifts: 21, reasons: ['QUOTA_OVERTIME'] }))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1. Lý do chính: hạn mức ca ngoài giờ mỗi tuần.')
    expect(getScheduleDetailStaffingMessage('SUCCESS', { requiredEmployees: 10, availableEmployees: 1, totalShifts: 21, reasons: [] })).toBe('')
  })
```

In `scheduleAdapter.test.ts`, assert `adaptBackendPeriodSchedule` maps `latestOptimizationRun.staffingAdvice` onto `detail.staffingAdvice`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm.cmd test -- ScheduleDetailPage.helpers scheduleAdapter`
Expected: FAIL because the types and helper do not exist.

- [ ] **Step 3: Add the types.** In `types/backendSchedule.ts`, extend `BackendLatestOptimizationRun`:

```ts
  staffingAdvice?: {
    requiredEmployees: number
    availableEmployees: number
    totalShifts: number
    reasons: string[]
  } | null
```

In `types/schedule.ts`, add to `ScheduleDetail`:

```ts
export interface ScheduleStaffingAdvice {
  requiredEmployees: number
  availableEmployees: number
  totalShifts: number
  reasons: string[]
}
```

and add `staffingAdvice?: ScheduleStaffingAdvice | null` to `ScheduleDetail`.

- [ ] **Step 4: Map it in the adapter** in `adaptBackendPeriodSchedule`, adding to the returned object:

```ts
    staffingAdvice: schedule.latestOptimizationRun?.staffingAdvice ?? null,
```

- [ ] **Step 5: Implement the helper** in `ScheduleDetailPage.helpers.ts`:

```ts
const staffingReasonLabels: Record<string, string> = {
  WEEKLY_CAPACITY: 'giới hạn số ca mỗi tuần',
  QUOTA_OVERTIME: 'hạn mức ca ngoài giờ mỗi tuần',
  QUOTA_BATHING: 'hạn mức ca thông tầm mỗi tuần',
  ON_CALL_GAP: 'khoảng cách giữa các ngày trực',
  SAME_DAY_REST: 'thời gian nghỉ tối thiểu giữa các ca',
}

export const getScheduleDetailStaffingMessage = (
  optimizationStatus?: string | null,
  advice?: ScheduleStaffingAdvice | null,
) => {
  if (optimizationStatus !== 'INFEASIBLE' || !advice) return ''
  const reason = advice.reasons.map((code) => staffingReasonLabels[code]).find(Boolean)
  const suffix = reason ? ` Lý do chính: ${reason}.` : ''
  return `Lịch này cần tối thiểu ${advice.requiredEmployees} nhân viên nhưng pool hiện chỉ có ${advice.availableEmployees}.${suffix}`
}
```

- [ ] **Step 6: Render it** in `ScheduleDetailPage` next to the existing IFE warning block, and add the pool editor button right after it so the manager can fix the pool from the same place.

- [ ] **Step 7: Run the full verification suite**

Run: `npm.cmd test`, `npm.cmd run build`, `npm.cmd run lint`
Expected: all tests pass, build succeeds, 0 lint errors.

Run: `./mvnw.cmd -q '-Dtest=*SchedulePeriod*Test,*Optimization*Test,*ShiftAssignment*Test' test`
Expected: BUILD_OK

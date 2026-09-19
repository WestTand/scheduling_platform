# Backend-AI Scheduling Contract

Tai lieu nay dinh nghia contract giua Spring Boot backend va FastAPI AI service cho chuc nang tao lich tu dong bang Google OR-Tools CP-SAT. Contract nay dua tren PR `Implement CP-SAT scheduling optimizer` (#11) va yeu cau project ve hard/soft constraints.

## Muc Tieu

- Backend gom snapshot du lieu lap lich tu database va gui sang AI service.
- AI service toi uu lich bang OR-Tools CP-SAT va tra ve ket qua co cau truc.
- Backend luu ket qua vao `shift_assignments`, `optimization_runs`, `total_penalty`, va diagnostics.
- LLM neu co chi dung de giai thich diagnostics/gop y cho manager, khong thay OR-Tools quyet dinh assignment cuoi cung.

## Endpoint

```http
POST /api/v1/scheduling/optimize
Content-Type: application/json
```

Response status:

- `200 OK`: AI service xu ly thanh cong ve mat ky thuat. Body co `status` la `SUCCESS`, `INFEASIBLE`, hoac `FAILED`.
- `422 Unprocessable Entity`: request sai schema hoac co reference khong hop le, vi du locked assignment tro den employee khong ton tai trong payload.
- `5xx`: loi service khong mong doi. Backend nen luu `OptimizationRun.status = FAILED`.

## Backend Responsibilities

Backend phai chi gui du lieu da duoc loc va hop le:

- Chi gui employee dang active va account hop le.
- Chi gui shift type dang active.
- Chi gui work shift thuoc schedule period dang tao lich.
- Chi gui leave request da `APPROVED`.
- Chi gui preference hop le cho period.
- Chi gui locked assignment ma manager da khoa.
- Convert scheduling rules tu Admin Scheduling Rules API sang `rules` object.
- Tao `runId` tu `OptimizationRun` truoc khi goi AI service.
- Moi datetime gui sang AI service phai co UTC offset, vi du `2026-09-14T08:00:00+07:00`.

Backend khong nen gui employee/shift type inactive roi mong AI service tu loc tiep.

## AI Service Responsibilities

AI service phai:

- Validate request schema va reference id.
- Build CP-SAT model tu payload.
- Enforce hard constraints.
- Optimize soft penalties theo weights.
- Tra assignments neu co lich feasible.
- Tra diagnostics co cau truc neu infeasible.
- Khong sua database truc tiep.
- Khong tu goi backend de lay them data trong request hien tai.

## Request Schema

Top-level request:

```json
{
  "schemaVersion": "1.0",
  "runId": 10,
  "period": {},
  "employees": [],
  "shiftTypes": [],
  "workShifts": [],
  "approvedLeaves": [],
  "preferences": [],
  "lockedAssignments": [],
  "boundaryAssignments": [],
  "rules": {},
  "solverOptions": {}
}
```

### `schemaVersion`

Literal string. Current value:

```json
"1.0"
```

Tang version khi co breaking change trong payload hoac response.

> Shift-rule foundation slice khong doi schema: `schemaVersion` giu `"1.0"`. Backend chi luu `shiftCode` + `penaltyWeightMultiplier` trong DB, chua gui multiplier sang AI service.

### `runId`

Backend `optimization_runs.run_id`.

```json
10
```

### `period`

```json
{
  "periodId": 20,
  "startDate": "2026-09-14",
  "endDate": "2026-09-20",
  "versionNo": 1,
  "timezone": "Asia/Ho_Chi_Minh"
}
```

Rules:

- `endDate >= startDate`.
- `timezone` default nen la `Asia/Ho_Chi_Minh`.
- Work shifts phai nam trong `[startDate, endDate]`.

### `employees`

```json
[
  { "employeeId": 1 },
  { "employeeId": 2 }
]
```

Rules:

- `employeeId` unique trong payload.
- Backend chi gui employees active.

### `shiftTypes`

```json
[
  {
    "shiftTypeId": 100,
    "durationMinutes": 480,
    "overnight": false,
    "heavy": false,
    "minStaff": 1,
    "maxStaff": 2,
    "quotaCategory": "OVERTIME"
  }
]
```

Fields:

- `shiftTypeId`: backend `shift_types.shift_type_id`.
- `durationMinutes`: duration cua shift type, integer minutes.
- `overnight`: ca qua ngay.
- `heavy`: ca nang, dung cho heavy-shift penalty.
- `minStaff`, `maxStaff`: staff bounds tu shift type.
- `quotaCategory`: mot trong `OVERTIME`, `BATHING`, `ON_CALL`, hoac `null`.

Important:

- `quotaCategory` phai la stable enum do backend map tu cau hinh, khong infer tu display name.
- AI quota semantics: `OVERTIME` and `BATHING` quotas are counted by distinct `workDate` per employee per ISO week. Multiple same-category work shifts on the same `workDate` count once. Boundary assignments count by `startsAt.date()`. The field names `overtimeShiftsPerWeek` and `bathingShiftsPerWeek` are kept for `schemaVersion = "1.0"` compatibility even though their semantics are day-based.
- Shift calendar foundation (backend-only, chua gui sang AI): backend luu `shiftCode` (canonical key, khong key logic theo display name) va `penaltyWeightMultiplier` (da persist, AI schema `1.0` chua nhan multiplier). Quota category contract (`OVERTIME`, `BATHING`, `ON_CALL`, hoac `null`) giu nguyen trong slice nay.

### `workShifts`

```json
[
  {
    "workShiftId": 1000,
    "shiftTypeId": 100,
    "workDate": "2026-09-14",
    "startsAt": "2026-09-14T08:00:00+07:00",
    "endsAt": "2026-09-14T16:00:00+07:00",
    "requiredStaff": 1
  }
]
```

Rules:

- `workShiftId` unique.
- `shiftTypeId` phai ton tai trong `shiftTypes`.
- `workDate` phai nam trong period.
- `startsAt.date == workDate`.
- `endsAt > startsAt`.
- `requiredStaff > 0` va nam trong `[shiftType.minStaff, shiftType.maxStaff]`.
- `startsAt` va `endsAt` phai co timezone offset.

> Scheduling accuracy windows slice: backend now persists exact `startsAt`/`endsAt` on `work_shifts`; this slice does not change `schemaVersion` from `1.0`; later AI payload slice can switch snapshot assembly to persisted windows if not already doing so.

### `approvedLeaves`

```json
[
  {
    "leaveRequestId": 501,
    "employeeId": 1,
    "startDate": "2026-09-14",
    "endDate": "2026-09-15"
  }
]
```

Rules:

- Chi gui leave request da approved.
- `employeeId` phai ton tai trong `employees`.
- AI service hard-block employee trong range ngay nghi.

### `preferences`

```json
[
  {
    "preferenceId": 70,
    "employeeId": 2,
    "shiftTypeId": 100,
    "preferenceDate": "2026-09-14",
    "preferenceType": "WANT",
    "priority": 3
  }
]
```

Fields:

- `preferenceType`: `WANT` hoac `AVOID`.
- `shiftTypeId`: nullable. Neu null thi preference ap dung cho moi shift trong ngay do.
- `priority`: integer >= 1.

Preferences la soft constraints. Solver co the vi pham neu can de thoa hard constraints.

### `lockedAssignments`

```json
[
  {
    "assignmentId": 50,
    "employeeId": 2,
    "workShiftId": 1000
  }
]
```

Rules:

- `employeeId` phai ton tai trong `employees`.
- `workShiftId` phai ton tai trong `workShifts`.
- AI service hard-enforce assignment nay.
- Neu locked assignment conflict voi leave/rest/quota, solver co the tra `INFEASIBLE`.

### `boundaryAssignments`

Assignments ngay truoc hoac sau period, dung de enforce rest-time va quota khi period khong du context.

```json
[
  {
    "employeeId": 1,
    "shiftTypeId": 100,
    "startsAt": "2026-09-13T20:00:00+07:00",
    "endsAt": "2026-09-14T04:00:00+07:00"
  }
]
```

Rules:

- Backend nen gui assignment published/locked gan bien period neu can check rest-time.
- `startsAt`, `endsAt` phai co timezone offset.

### `rules`

Rules map tu Admin Scheduling Rules API.

```json
{
  "hardRules": {
    "minRestHoursBetweenShifts": 12,
    "maxWorkHoursPerDay": 12,
    "maxShiftsPerWeek": 6
  },
  "quotas": {
    "overtimeShiftsPerWeek": 1,
    "bathingShiftsPerWeek": 2,
    "onCallEveryNDays": 6
  },
  "softWeights": {
    "fairness": 10,
    "preference": 5,
    "weekend": 5,
    "heavyShift": 8,
    "workHourBalance": 10
  }
}
```

Hard rules:

- `minRestHoursBetweenShifts`: minimum rest between two assigned shifts.
- `maxWorkHoursPerDay`: maximum total assigned minutes per employee per day.
- `maxShiftsPerWeek`: maximum assigned work shifts per employee per ISO week.

Quotas:

- `overtimeShiftsPerWeek`: max distinct `OVERTIME` work dates per employee per ISO week. Legacy field name kept for schema `1.0` compatibility.
- `bathingShiftsPerWeek`: max distinct `BATHING` work dates per employee per ISO week. Legacy field name kept for schema `1.0` compatibility.
- `onCallEveryNDays`: minimum day gap between `ON_CALL` assignments.

Soft weights:

- `fairness`: penalty for shift count imbalance.
- `preference`: penalty for unmet WANT/AVOID preferences.
- `weekend`: penalty for weekend assignment imbalance.
- `heavyShift`: penalty for consecutive heavy shifts.
- `workHourBalance`: penalty for workload imbalance.

### `solverOptions`

```json
{
  "timeLimitSeconds": 30,
  "randomSeed": 1,
  "numWorkers": 8
}
```

Rules:

- `timeLimitSeconds`: `0 < value <= 300`.
- `randomSeed`: integer >= 0.
- `numWorkers`: `1 <= value <= 64`.

Backend can use lower time limit for preview/demo and higher for final optimize.

## Response Schema

Top-level response:

```json
{
  "schemaVersion": "1.0",
  "runId": 10,
  "periodId": 20,
  "status": "SUCCESS",
  "solverStatus": "OPTIMAL",
  "assignments": [],
  "totalPenalty": 0,
  "penaltyDetails": {},
  "warnings": [],
  "diagnostics": [],
  "errorMessage": null,
  "statistics": {}
}
```

### `status`

- `SUCCESS`: solver found feasible assignments.
- `INFEASIBLE`: no schedule satisfies all hard constraints.
- `FAILED`: solver stopped without feasible schedule, model invalid, or service-level failure represented in body.

### `solverStatus`

OR-Tools status string:

- `OPTIMAL`
- `FEASIBLE`
- `INFEASIBLE`
- `UNKNOWN`
- `MODEL_INVALID`

### `assignments`

Only populated when `status = SUCCESS`.

```json
[
  {
    "employeeId": 2,
    "workShiftId": 1000
  }
]
```

Backend maps each item to `ShiftAssignment`:

- `work_shift_id = workShiftId`
- `employee_id = employeeId`
- `assigned_source = AUTO`
- `assignment_status = ASSIGNED`
- `is_locked = false` unless backend decides otherwise

Locked assignments returned by AI can be persisted as existing locked records instead of duplicated.

### `totalPenalty`

Numeric total objective penalty after scaling back to decimal units. Lower is better.

Null when no feasible assignment exists.

### `penaltyDetails`

```json
{
  "fairness": 0,
  "preference": 5,
  "weekend": 0,
  "heavyShift": 0,
  "workHourBalance": 10
}
```

Backend stores this in `optimization_runs.penalty_details_json`.

### `warnings`

Human-readable warnings. Example:

```json
[
  "A feasible schedule was found, but optimality was not proven"
]
```

Warnings do not block saving schedule, but UI should show them.

### `diagnostics`

Machine-readable reasons for infeasible or problematic results.

```json
[
  {
    "code": "INSUFFICIENT_AVAILABLE_STAFF",
    "message": "Approved leave leaves too few employees for this shift",
    "employeeId": null,
    "workShiftId": 1000
  }
]
```

Known current codes:

- `NO_FEASIBLE_SCHEDULE`
- `INSUFFICIENT_AVAILABLE_STAFF`

Recommended follow-up codes:

- `LOCKED_ASSIGNMENT_CONFLICT`
- `REST_TIME_CONFLICT`
- `QUOTA_TOO_STRICT`
- `MAX_WORK_HOURS_TOO_LOW`
- `MAX_SHIFTS_PER_WEEK_TOO_LOW`
- `TOO_MANY_APPROVED_LEAVES`
- `REQUIRED_STAFF_EXCEEDS_ACTIVE_EMPLOYEES`

LLM explanation layer should consume diagnostics and produce user-friendly explanation. LLM must not invent diagnostics that are not supported by structured solver/backend evidence.

### `errorMessage`

String summary for `INFEASIBLE` or `FAILED`.

### `statistics`

```json
{
  "wallTimeSeconds": 0.15,
  "conflicts": 0,
  "branches": 12
}
```

Backend may store this in `optimization_runs` if a JSON column is added later, or keep it in logs for now.

## Full Example Request

```json
{
  "schemaVersion": "1.0",
  "runId": 10,
  "period": {
    "periodId": 20,
    "startDate": "2026-09-14",
    "endDate": "2026-09-15",
    "versionNo": 1,
    "timezone": "Asia/Ho_Chi_Minh"
  },
  "employees": [
    { "employeeId": 1 },
    { "employeeId": 2 }
  ],
  "shiftTypes": [
    {
      "shiftTypeId": 100,
      "durationMinutes": 480,
      "overnight": false,
      "heavy": false,
      "minStaff": 1,
      "maxStaff": 2,
      "quotaCategory": "OVERTIME"
    }
  ],
  "workShifts": [
    {
      "workShiftId": 1000,
      "shiftTypeId": 100,
      "workDate": "2026-09-14",
      "startsAt": "2026-09-14T08:00:00+07:00",
      "endsAt": "2026-09-14T16:00:00+07:00",
      "requiredStaff": 1
    }
  ],
  "approvedLeaves": [],
  "preferences": [
    {
      "preferenceId": 70,
      "employeeId": 2,
      "shiftTypeId": 100,
      "preferenceDate": "2026-09-14",
      "preferenceType": "WANT",
      "priority": 3
    }
  ],
  "lockedAssignments": [],
  "boundaryAssignments": [],
  "rules": {
    "hardRules": {
      "minRestHoursBetweenShifts": 12,
      "maxWorkHoursPerDay": 12,
      "maxShiftsPerWeek": 6
    },
    "quotas": {
      "overtimeShiftsPerWeek": 1,
      "bathingShiftsPerWeek": 2,
      "onCallEveryNDays": 6
    },
    "softWeights": {
      "fairness": 10,
      "preference": 5,
      "weekend": 5,
      "heavyShift": 8,
      "workHourBalance": 10
    }
  },
  "solverOptions": {
    "timeLimitSeconds": 30,
    "randomSeed": 1,
    "numWorkers": 8
  }
}
```

## Full Example Success Response

```json
{
  "schemaVersion": "1.0",
  "runId": 10,
  "periodId": 20,
  "status": "SUCCESS",
  "solverStatus": "OPTIMAL",
  "assignments": [
    { "employeeId": 2, "workShiftId": 1000 }
  ],
  "totalPenalty": 0,
  "penaltyDetails": {
    "fairness": 0,
    "preference": 0,
    "weekend": 0,
    "heavyShift": 0,
    "workHourBalance": 0
  },
  "warnings": [],
  "diagnostics": [],
  "errorMessage": null,
  "statistics": {
    "wallTimeSeconds": 0.15,
    "conflicts": 0,
    "branches": 12
  }
}
```

## Full Example Infeasible Response

```json
{
  "schemaVersion": "1.0",
  "runId": 10,
  "periodId": 20,
  "status": "INFEASIBLE",
  "solverStatus": "INFEASIBLE",
  "assignments": [],
  "totalPenalty": null,
  "penaltyDetails": {},
  "warnings": [],
  "diagnostics": [
    {
      "code": "NO_FEASIBLE_SCHEDULE",
      "message": "No schedule satisfies all hard constraints",
      "employeeId": null,
      "workShiftId": null
    },
    {
      "code": "INSUFFICIENT_AVAILABLE_STAFF",
      "message": "Approved leave leaves too few employees for this shift",
      "employeeId": null,
      "workShiftId": 1000
    }
  ],
  "errorMessage": "No feasible schedule found",
  "statistics": {
    "wallTimeSeconds": 0.04,
    "conflicts": 0,
    "branches": 0
  }
}
```

## Backend Handling Rules

When response `status = SUCCESS`:

1. Delete or replace non-locked auto assignments for the period according to backend scheduling policy.
2. Preserve existing locked assignments.
3. Insert returned assignments as `AUTO` assignments.
4. Set schedule period status to `PENDING_APPROVAL` or equivalent draft-review status.
5. Save `OptimizationRun.status = SUCCESS`.
6. Save `totalPenalty` and `penaltyDetails`.

When response `status = INFEASIBLE`:

1. Do not create new assignments.
2. Save `OptimizationRun.status = INFEASIBLE`.
3. Save `errorMessage` and diagnostics.
4. Show manager diagnostics and suggested follow-up actions.

When HTTP error or response `status = FAILED`:

1. Do not create assignments.
2. Save `OptimizationRun.status = FAILED`.
3. Save error summary.
4. Allow manager to retry after fixing inputs or service availability.

## LLM Explanation Contract

LLM layer is optional and should run after solver result exists.

Input to LLM should be:

- Solver `status`.
- Structured `diagnostics`.
- High-level schedule stats.
- Relevant rule values.

LLM output should be:

- Vietnamese explanation for manager.
- Suggested actions.
- No new assignment decisions.

LLM must not override hard constraints or fabricate root causes not present in diagnostics.

## Open Follow-ups

- Quota count ambiguity resolved: `OVERTIME` and `BATHING` use distinct work-date counts per employee per ISO week while retaining legacy schema `1.0` field names.
- Add richer infeasible diagnostics for quota conflicts, locked assignment conflicts, rest conflicts, and max-hour conflicts.
- Add backend integration tests with mocked AI service.
- Add retry/timeout policy for backend HTTP call to AI service.
- Add OpenAPI examples to AI service after contract stabilizes.

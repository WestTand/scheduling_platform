# Employee Schedule View APIs Design

## Goal

Let employees see their own published shifts and let managers inspect any employee's workload across periods. This closes the loop opened by the approve/publish slice: a published schedule becomes visible to the people who work it.

This slice does not add swap requests, notifications, leave requests, preferences, update-in-place for assignments, AI-service invocation, or frontend screens.

## Scope

Add two read-only endpoints:

- `GET /api/employee/schedule` (role `EMPLOYEE`, own schedule only)
- `GET /api/admin/employees/{employeeId}/schedule` (role `MANAGER`, any employee)

Implementation follows approach A from brainstorming: one service per package boundary (`EmployeeScheduleService` in `employee`, new method in the admin assignment area for managers), sharing one response DTO and one date-range normalization helper. Access control stays separated per side, matching the existing `employee/*` vs `admin/*` split.

## Endpoint Behavior

### Employee Own Schedule

```http
GET /api/employee/schedule?from=2026-09-17&to=2026-10-17
```

Rules:

- Requires `ROLE_EMPLOYEE` plus `EmployeeAccess.requireActiveEmployee`, which resolves the employee from the calling account.
- Returns only assignments whose work shift belongs to a `PUBLISHED` period. Draft, approved-but-unpublished, and cancelled periods are invisible to employees.
- `from`/`to` are optional ISO dates. When omitted, the range defaults to today through today plus 30 days. `from` after `to` is `400 BAD_REQUEST`.
- Results are ordered by work date ascending, then shift start time ascending.

### Manager View By Employee

```http
GET /api/admin/employees/{employeeId}/schedule?from=2026-09-17&to=2026-10-17
```

Rules:

- Requires `ROLE_MANAGER` plus the existing service-level `MANAGER` role check.
- `employeeId` must reference an existing employee; otherwise `404 NOT_FOUND`. Any existing employee qualifies regardless of `Employee.status`; status filtering is out of scope.
- Returns assignments in all period statuses (`DRAFT`, `APPROVED`, `PUBLISHED`, `CANCELLED`) so managers can review workload while a schedule is still being built.
- Same `from`/`to` semantics as the employee endpoint: optional, default today through today plus 30 days, `from` after `to` is `400 BAD_REQUEST`.
- Same ordering: work date ascending, then shift start time ascending.

### Date Handling

"Today" for the default range is `LocalDate.now(clock)` from a constructor-injected `java.time.Clock`. In production Spring wires the existing `TimeConfig` clock bean (`Clock.systemUTC()`), so the default window is UTC-date based; tests pass `Clock.fixed(...)` directly to assert the exact default window of today through today plus 30 days. Controllers pass raw nullable `from`/`to` through; services normalize the range.

## Response Shape

Both endpoints return a list of the same item DTO:

```json
[
  {
    "assignmentId": 300,
    "assignmentStatus": "ASSIGNED",
    "assignedSource": "MANUAL",
    "locked": false,
    "workDate": "2026-09-17",
    "shiftTypeId": 100,
    "shiftTypeName": "Day",
    "shiftStartTime": "08:00",
    "shiftEndTime": "16:00",
    "periodId": 20,
    "periodName": "Week 38",
    "periodStatus": "PUBLISHED"
  }
]
```

An empty list (no assignments in range) is `200 OK` with `[]`, not an error.

## Validation And Errors

- `400 BAD_REQUEST`: `from` after `to`, unparseable date parameters.
- `403 FORBIDDEN`: missing principal, missing account, wrong role, or inactive employee (reuse the existing `EmployeeAccess`/`requireManager` behavior unchanged).
- `404 NOT_FOUND`: manager queries a non-existent `employeeId`; employee account has no linked employee profile (existing `EmployeeAccess` behavior, kept as-is).

## Persistence And Repository

No new repository interface is required; services inject the existing `ShiftAssignmentRepository` and `EmployeeRepository`:

- `ShiftAssignmentRepository.findForEmployeeInWindow` must be extended in this slice to `join fetch ws.schedulePeriod sp` and to add `order by ws.workDate asc, st.startTime asc, sa.id asc`, matching the ordering contract in Endpoint Behavior. The current query only fetch-joins work shift and shift type and has no ordering.
- The employee endpoint filters the result to `PUBLISHED` periods in service code. The manager endpoint returns all statuses.
- The manager endpoint loads the employee with `EmployeeRepository.findById`; a missing row is `404 NOT_FOUND`.

## Testing

Service tests cover:

- employee sees only `PUBLISHED` assignments; `DRAFT`/`APPROVED`/`CANCELLED` assignments in range are excluded;
- manager sees assignments in all statuses for the given employee;
- missing `from`/`to` defaults to today through today plus 30 days (assert the repository receives the computed window);
- explicit `from`/`to` is passed through to the repository;
- `from` after `to` is `400`;
- manager gets `404` for an unknown `employeeId`;
- non-employee/wrong-role access is `403` on both sides.

Controller tests cover:

- employee schedule returns `200` and delegates principal plus raw nullable `from`/`to` (service normalizes the default range);
- manager schedule-by-employee returns `200` and delegates principal, path id, and raw nullable `from`/`to`;
- non-employee access to the employee endpoint is forbidden;
- non-manager access to the manager endpoint is forbidden;
- anonymous access to both endpoints is unauthorized.

Full backend verification remains `.\backend\mvnw.cmd -f backend/pom.xml clean test` from the repo root.

## Out Of Scope

- Swap requests and swap approvals.
- Notifications on publish or assignment changes.
- Leave requests and preferences.
- Assignment create/update/delete/lock from these endpoints (read-only).
- AI-service invocation and explanation calls.
- Frontend screens.

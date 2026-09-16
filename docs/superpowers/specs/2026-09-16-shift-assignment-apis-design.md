# ShiftAssignment APIs Design

## Goal

Implement the next Phase 5 manual scheduling slice: manager APIs for assigning employees to concrete work shifts inside a draft schedule period, viewing the period schedule with assignments, and locking assignments so the future AI scheduler will not overwrite them.

This slice makes manual schedules usable end to end at the assignment level. It does not update assignments in place, add view-by-employee, validate rest/quota rules, approve/publish periods, call the AI service, or build frontend screens.

## Scope

Add manager-only APIs:

- `POST /api/admin/work-shifts/{workShiftId}/assignments`
- `GET /api/admin/schedule-periods/{periodId}/schedule`
- `DELETE /api/admin/assignments/{assignmentId}`
- `PATCH /api/admin/assignments/{assignmentId}/lock`
- `PATCH /api/admin/assignments/{assignmentId}/unlock`

All endpoints require `ROLE_MANAGER`. The service must also verify that the current account exists and has role `MANAGER`, matching the SchedulePeriod and WorkShift service pattern.

## Data Model

Use the existing `ShiftAssignment` entity. No schema changes are required:

- `workShift`: target `WorkShift`.
- `employee`: assigned `Employee`.
- `assignmentStatus`: defaults to `ASSIGNED`.
- `assignedSource`: manual creates use `MANUAL`.
- `locked`: defaults to `false`; lock/unlock endpoints flip this flag.

The existing unique constraint on `(work_shift_id, employee_id)` prevents duplicate assignments.

## Endpoint Behavior

### Assign Employee To Work Shift

```http
POST /api/admin/work-shifts/{workShiftId}/assignments
Content-Type: application/json
```

Request:

```json
{
  "employeeId": 7
}
```

Rules:

- `workShiftId` must exist.
- Parent schedule period must be `DRAFT` (this DRAFT requirement applies to all mutating endpoints in this slice, not to the read-only schedule view below).
- `employeeId` is required and must reference an existing `Employee` with `status = ACTIVE`.
- The same employee must not already be assigned to the same work shift (return `409 CONFLICT`).
- The work shift must still have capacity: current assignment count must be below `shiftType.maxStaff`. `WorkShift.requiredStaff` is the target staffing level; `shiftType.maxStaff` is the hard upper bound that also permits deliberate manual overstaffing above the target.
- The employee must not have an `APPROVED` leave request covering `workShift.workDate` (`startDate <= workDate <= endDate`).
- The employee must not have another assignment whose real time interval overlaps the target shift interval. Build each interval from `workDate + shiftType.startTime/endTime`; when the shift type is overnight (`endTime <= startTime`), the interval ends on `workDate + 1`. Intervals that only touch at one endpoint do not overlap. The overlap check must cover a date window of `[workDate - 1 day, workDate + 1 day]` across all periods, not only assignments inside the target shift's own period, so overnight shifts spilling over period boundaries are still detected.
- New assignments store `assignmentStatus = ASSIGNED`, `assignedSource = MANUAL`, `locked = false`.

Response status: `201 Created`.

### View Period Schedule With Assignments

```http
GET /api/admin/schedule-periods/{periodId}/schedule
```

Rules:

- `periodId` must exist.
- This is a read-only endpoint and is allowed for all period statuses, including non-`DRAFT` periods.
- Returns the period summary plus all work shifts ordered by `workDate ASC`, `shiftType.startTime ASC`, then work shift id `ASC`.
- Each work shift includes its assignments ordered by assignment id `ASC`.
- This is the read model the future calendar/table UI and approval flow will use.

### Delete Assignment

```http
DELETE /api/admin/assignments/{assignmentId}
```

Rules:

- `assignmentId` must exist.
- Parent schedule period must be `DRAFT`.
- Deletion is allowed even when the assignment is locked. `locked` is only a flag telling the future AI scheduler not to overwrite the row; it does not block manual management while the period is still a draft.
- Response status: `204 No Content`.

### Lock And Unlock Assignment

```http
PATCH /api/admin/assignments/{assignmentId}/lock
PATCH /api/admin/assignments/{assignmentId}/unlock
```

Rules:

- `assignmentId` must exist.
- Parent schedule period must be `DRAFT`.
- `lock` sets `locked = true`; `unlock` sets `locked = false`. Both are idempotent.
- Response returns the updated assignment.

## Response Shape

Assignment:

```json
{
  "id": 500,
  "workShiftId": 200,
  "periodId": 20,
  "employeeId": 7,
  "employeeCode": "NV007",
  "fullName": "Nguyen Van A",
  "assignmentStatus": "ASSIGNED",
  "assignedSource": "MANUAL",
  "locked": false
}
```

Period schedule:

```json
{
  "periodId": 20,
  "periodName": "Week 38",
  "status": "DRAFT",
  "startDate": "2026-09-14",
  "endDate": "2026-09-20",
  "workShifts": [
    {
      "id": 200,
      "workDate": "2026-09-14",
      "shiftTypeId": 100,
      "shiftName": "Morning",
      "requiredStaff": 2,
      "assignments": [
        {
          "id": 500,
          "employeeId": 7,
          "employeeCode": "NV007",
          "fullName": "Nguyen Van A",
          "assignmentStatus": "ASSIGNED",
          "assignedSource": "MANUAL",
          "locked": false
        }
      ]
    }
  ]
}
```

## Validation And Errors

- `400 BAD_REQUEST`: period is not `DRAFT` (mutating endpoints only; the schedule view accepts any status), employee is inactive, shift is already at `maxStaff`, approved leave covers the shift date, overlapping assignment for the same employee, missing `employeeId`.
- `403 FORBIDDEN`: authenticated principal is missing, account is missing, or account role is not `MANAGER`.
- `404 NOT_FOUND`: work shift, schedule period, employee, or assignment does not exist.
- `409 CONFLICT`: the same employee is already assigned to the same work shift.

## Persistence And Repository

Add `ShiftAssignmentRepository` with methods for:

- detail lookup by id with work shift, period, shift type, and employee fetched;
- counting assignments for one work shift for the capacity check;
- loading one employee's assignments in the `[workDate - 1 day, workDate + 1 day]` window with shift time fields for the cross-period overlap check;
- listing assignments for a period schedule ordered by shift date, shift start time, and assignment id.

Fetch joins should cover the associations needed by the response DTOs to avoid lazy-loading surprises.

## Testing

Service tests cover:

- manager assigns an active employee in a draft period with `MANUAL` source;
- assign rejects missing/non-manager account;
- assign rejects missing work shift, missing employee, inactive employee, and non-draft period;
- assign rejects duplicates with `409`;
- assign rejects full shifts at `maxStaff`;
- assign rejects approved leave covering the shift date;
- assign rejects overlapping shifts including overnight intervals, while back-to-back touching intervals pass;
- delete removes an assignment in a draft period, including locked ones;
- delete rejects non-draft periods and missing assignments;
- lock/unlock flip the flag, are idempotent, and reject non-draft periods;
- schedule view validates manager, returns ordered shifts with nested assignments.

Controller tests cover:

- manager assign returns `201 Created` and delegates principal/path id/request;
- manager schedule view, delete (`204`), lock, and unlock;
- request body validation returns `400` and service is not called;
- non-manager access is forbidden;
- anonymous access is unauthorized.

Full backend verification remains `backend/.\mvnw.cmd clean test`.

## Out Of Scope

- Updating an assignment in place (use delete plus recreate).
- View schedule by employee.
- Rest, max-hours-per-day, and weekly quota validation (reserved for the AI solver).
- Schedule approval or publish state transitions.
- AI-service invocation and explanation calls.
- Swap requests, notifications, and frontend screens.

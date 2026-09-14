# WorkShift APIs Design

## Goal

Implement the next Phase 5 manual scheduling slice: manager APIs for creating and viewing concrete work shifts inside a draft schedule period.

This slice creates the shift slots that manual assignment and AI scheduling will later fill with employees. It does not assign employees, update or delete work shifts, lock assignments, approve/publish schedules, call the AI service, or create bulk work shifts.

## Scope

Add manager-only APIs:

- `POST /api/admin/schedule-periods/{periodId}/work-shifts`
- `GET /api/admin/schedule-periods/{periodId}/work-shifts`
- `GET /api/admin/work-shifts/{workShiftId}`

All endpoints require `ROLE_MANAGER`. The service must also verify that the current account exists and has role `MANAGER`, matching the SchedulePeriod service pattern.

## Data Model

Use the existing `WorkShift` entity:

- `schedulePeriod`: parent `SchedulePeriod`.
- `shiftType`: selected `ShiftType`.
- `workDate`: date of this concrete shift.
- `requiredStaff`: number of employees needed for the shift.
- `note`: optional manager note.

No schema changes are required.

## Endpoint Behavior

### Create Work Shift

```http
POST /api/admin/schedule-periods/{periodId}/work-shifts
Content-Type: application/json
```

Request:

```json
{
  "shiftTypeId": 100,
  "workDate": "2026-09-14",
  "requiredStaff": 2,
  "note": "Extra coverage for morning round"
}
```

Rules:

- `periodId` must exist.
- Schedule period must be `DRAFT`.
- `shiftTypeId` is required and must reference an existing `ShiftType` with `status = ACTIVE`.
- `workDate` is required and must be inside `[period.startDate, period.endDate]`.
- `requiredStaff` is optional. If omitted, default to `shiftType.minStaff`.
- `requiredStaff` must be inside `[shiftType.minStaff, shiftType.maxStaff]`.
- `note` is optional. If blank, store `null`. If present, trim and limit to 1000 characters after trim.

Response status: `201 Created`.

### List Work Shifts In Period

```http
GET /api/admin/schedule-periods/{periodId}/work-shifts?date=&shiftTypeId=
```

Rules:

- `periodId` must exist.
- Optional `date` filter returns shifts for one `workDate` inside the period.
- Optional `shiftTypeId` filter returns shifts for one shift type.
- Results are ordered by `workDate ASC`, `shiftType.startTime ASC`, then `id ASC`.

This endpoint is manager-facing and returns all work shifts in the period regardless of future assignments.

### Get Work Shift Detail

```http
GET /api/admin/work-shifts/{workShiftId}
```

Rules:

- `workShiftId` must exist.
- Response includes parent period summary and shift type summary.

## Response Shape

```json
{
  "id": 200,
  "periodId": 20,
  "periodName": "Week 38",
  "shiftTypeId": 100,
  "shiftName": "Morning",
  "shiftStartTime": "08:00:00",
  "shiftEndTime": "16:00:00",
  "workDate": "2026-09-14",
  "requiredStaff": 2,
  "note": "Extra coverage for morning round"
}
```

The response intentionally does not include assignments. Assignment count/details will be added in a later manual assignment slice if needed.

## Validation And Errors

- `400 BAD_REQUEST`: period is not `DRAFT`, `workDate` outside period, inactive shift type, invalid `requiredStaff`, invalid filter date, overlong note.
- `403 FORBIDDEN`: authenticated principal is missing, account is missing, or account role is not `MANAGER`.
- `404 NOT_FOUND`: schedule period, shift type, or work shift does not exist.

No duplicate prevention is required in this slice. Multiple work shifts with the same `workDate` and `shiftTypeId` are allowed because a period may need repeated slots or extra coverage using the same type.

## Persistence And Repository

Add `WorkShiftRepository` with methods for:

- listing by period id with optional date and shift type filters;
- detail lookup by id with period and shift type fetched;
- optional period-scoped lookup if later assignment APIs need it.

Fetch joins should include `schedulePeriod` and `shiftType` when mapping response DTOs to avoid lazy-loading surprises.

## Testing

Service tests cover:

- manager creates work shift in a draft period;
- omitted `requiredStaff` defaults to `shiftType.minStaff`;
- blank note stores `null`, nonblank note trims;
- create rejects missing/non-manager account;
- create rejects missing period, non-draft period, missing shift type, inactive shift type;
- create rejects work date outside period;
- create rejects required staff outside shift type bounds;
- create rejects note longer than 1000 after trim;
- list validates manager and delegates filters/order query;
- detail validates manager and maps period/shift type summary;
- detail returns `404` for missing work shift.

Controller tests cover:

- manager create returns `201 Created` and delegates principal/request/path id;
- manager list with date and shiftType filters;
- manager detail;
- request body validation returns `400` and service is not called;
- non-manager access is forbidden;
- anonymous access is unauthorized.

Full backend verification remains `backend/.\mvnw.cmd clean test`.

## Out Of Scope

- Bulk generation across a date range.
- Update or delete work shifts.
- Manual shift assignment.
- Assignment conflict validation.
- Assignment lock/unlock.
- Schedule approval or publish.
- AI-service invocation.
- Frontend screens.

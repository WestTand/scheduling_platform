# Schedule Period Lifecycle APIs Design

## Goal

Implement the Phase 5 schedule approval slice: manager APIs for approving, publishing, and cancelling a schedule period. This is the step that turns a manually built draft schedule into an official schedule employees can rely on.

This slice does not add employee-facing schedule views, notifications, update-in-place for assignments, view-by-employee, AI-service invocation, or frontend screens.

## Scope

Add manager-only APIs to the existing schedule period controller:

- `PATCH /api/admin/schedule-periods/{periodId}/approve`
- `PATCH /api/admin/schedule-periods/{periodId}/publish`
- `PATCH /api/admin/schedule-periods/{periodId}/cancel`

All endpoints require `ROLE_MANAGER` plus the existing service-level `MANAGER` role check. Implementation goes into `AdminSchedulePeriodService`/`AdminSchedulePeriodController`, reusing `requireManager`, `findDetail`, and the `invalidRequest` pattern.

## State Model

Allowed transitions, no backward moves:

- `DRAFT -> APPROVED` via approve.
- `APPROVED -> PUBLISHED` via publish.
- `DRAFT -> CANCELLED` and `APPROVED -> CANCELLED` via cancel.
- `PUBLISHED` is terminal in this slice; cancelling a published period is out of scope until the Phase 7 notification flow exists.
- Repeating the same transition is idempotent and returns `200 OK` with the current state.

Field effects:

- approve sets `approvedBy` to the current manager account.
- publish sets `publishedAt` to the current timestamp; a repeated publish keeps the original `publishedAt`.
- cancel only flips `status`; it does not clear assignments, shifts, or approval fields.

## Endpoint Behavior

### Approve Period

```http
PATCH /api/admin/schedule-periods/{periodId}/approve
```

Rules:

- `periodId` must exist.
- Period must be `DRAFT`. Calling approve on an already `APPROVED` period re-validates staffing and returns the current state.
- Period must contain at least one work shift.
- Every work shift must have assignment count greater than or equal to its `requiredStaff`. Any understaffed shift fails the whole request with `400 BAD_REQUEST`.
- On success sets `status = APPROVED` and `approvedBy` to the calling manager.

### Publish Period

```http
PATCH /api/admin/schedule-periods/{periodId}/publish
```

Rules:

- `periodId` must exist.
- Period must be `APPROVED`. Calling publish on an already `PUBLISHED` period returns the current state without touching `publishedAt`.
- No staffing re-check; approval already certified staffing.
- On success sets `status = PUBLISHED` and `publishedAt` to now.

### Cancel Period

```http
PATCH /api/admin/schedule-periods/{periodId}/cancel
```

Rules:

- `periodId` must exist.
- Period must be `DRAFT` or `APPROVED`. Calling cancel on an already `CANCELLED` period returns the current state.
- No staffing check.
- On success sets `status = CANCELLED`.

## Freeze Effects

All existing mutation endpoints require `DRAFT` (`updateSchedulePeriod`, work shift creation, assignment create/delete/lock/unlock), so moving a period out of `DRAFT` automatically freezes it. No code changes to those endpoints are needed in this slice.

The period schedule view (`GET /api/admin/schedule-periods/{periodId}/schedule`) already supports all statuses and keeps working for approved, published, and cancelled periods.

## List Filter Extension

The period list endpoint (`GET /api/admin/schedule-periods`) currently only accepts the `DRAFT` status filter. Extend `normalizeStatus` to also accept `APPROVED`, `PUBLISHED`, and `CANCELLED` so managers can list periods in every lifecycle state. Omitting the filter keeps defaulting to `DRAFT` to preserve existing behavior.

## Response Shape

All three endpoints return the existing `AdminSchedulePeriodResponse`, which must now also expose the lifecycle fields:

```json
{
  "id": 20,
  "periodName": "Week 38",
  "startDate": "2026-09-14",
  "endDate": "2026-09-20",
  "versionNo": 1,
  "status": "APPROVED",
  "createdByAccountId": 1,
  "createdByUsername": "manager01",
  "approvedByAccountId": 1,
  "approvedByUsername": "manager01",
  "publishedAt": null,
  "totalPenalty": null
}
```

The current response DTO already exposes these lifecycle fields with null-safe mapping (null account or timestamp maps to null), so no DTO shape change is required.

## Validation And Errors

- `400 BAD_REQUEST`: wrong-state transition (e.g. publish from `DRAFT`, approve from `PUBLISHED`/`CANCELLED`, approve/publish/cancel targeting a different terminal state from `CANCELLED`), empty period on approve, understaffed shift on approve, invalid list status filter. Repeating the same state (approve on `APPROVED`, publish on `PUBLISHED`, cancel on `CANCELLED`) is idempotent `200 OK`, not an error.
- `403 FORBIDDEN`: authenticated principal is missing, account is missing, or account role is not `MANAGER`.
- `404 NOT_FOUND`: schedule period does not exist.

## Persistence And Repository

No new repository is required:

- Staffing check reuses `WorkShiftRepository.searchForPeriod(periodId, null, null)` for shifts and `ShiftAssignmentRepository.listForPeriod(periodId)` for assignment counts, grouped by work shift id.
- The period itself is loaded with the existing `SchedulePeriodRepository.findDetailById`, which already fetch-joins `createdBy`/`approvedBy`; extend the fetch to `approvedBy` if the response mapper needs the username (it is already fetched per the current query).

## Testing

Service tests cover:

- manager approves a fully staffed draft period, sets `APPROVED` and `approvedBy`;
- approve rejects missing/non-manager account;
- approve rejects missing period (`404`);
- approve rejects `PUBLISHED` and `CANCELLED` periods;
- re-approve on an already `APPROVED` period re-validates staffing: returns `200` when staffing still passes, `400` otherwise;
- approve rejects empty periods (no work shifts);
- approve rejects periods with any understaffed shift, and saves nothing;
- approve is idempotent for already-approved periods when staffing still passes;
- manager publishes an approved period, sets `PUBLISHED` and `publishedAt`;
- publish rejects `DRAFT`, `CANCELLED`, and missing periods;
- repeated publish keeps the original `publishedAt`;
- manager cancels from `DRAFT` and from `APPROVED`;
- cancel rejects `PUBLISHED` and missing periods;
- cancel on an already `CANCELLED` period is idempotent and returns `200`;
- list accepts the new status filters and still defaults to `DRAFT`.

Controller tests cover:

- manager approve/publish/cancel return `200` and delegate principal plus path id;
- non-manager access is forbidden;
- anonymous access is unauthorized.

Full backend verification remains `backend/.\mvnw.cmd clean test`.

## Out Of Scope

- Cancelling or unpublishing a `PUBLISHED` period.
- Reopening a period back to `DRAFT`.
- Employee-facing schedule views.
- Notifications on publish/cancel.
- Rest, max-hours, and quota validation (reserved for the AI solver).
- AI-service invocation and explanation calls.
- Swap requests and frontend screens.

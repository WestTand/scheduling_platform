# SchedulePeriod APIs Design

## Goal

Implement the first Phase 5 manual scheduling slice: manager APIs for creating and managing schedule periods in `DRAFT` status.

This slice creates the scheduling container needed before work shifts, manual assignments, and AI scheduling can run. It does not create work shifts, assign employees, approve periods, publish schedules, call the AI service, or expose employee schedule views.

## Scope

Add manager-only APIs under `/api/admin/schedule-periods`:

- `POST /api/admin/schedule-periods`
- `GET /api/admin/schedule-periods`
- `GET /api/admin/schedule-periods/{periodId}`
- `PUT /api/admin/schedule-periods/{periodId}`

All endpoints require `ROLE_MANAGER`.

## Status Model

This batch supports only `DRAFT` periods.

- Newly created periods always use `status = DRAFT`.
- Update is allowed only while the period is `DRAFT`.
- Clients cannot set `status`, `createdBy`, `approvedBy`, `publishedAt`, or `totalPenalty` through these APIs.
- Approve and publish transitions are out of scope for this batch.

## Request And Response Shape

### Create Request

```json
{
  "periodName": "Week 38 - September 2026",
  "startDate": "2026-09-14",
  "endDate": "2026-09-20",
  "versionNo": 1
}
```

Rules:

- `periodName` is required, trimmed, and limited to 100 characters.
- `startDate` and `endDate` are required.
- `startDate <= endDate`.
- `versionNo` is optional and defaults to `1`.
- `versionNo >= 1`.
- `(startDate, endDate, versionNo)` must be unique.

### Update Request

Same fields as create. Update replaces editable period fields after validation.

### Response

```json
{
  "id": 20,
  "periodName": "Week 38 - September 2026",
  "startDate": "2026-09-14",
  "endDate": "2026-09-20",
  "versionNo": 1,
  "status": "DRAFT",
  "createdByAccountId": 3,
  "createdByUsername": "manager01",
  "approvedByAccountId": null,
  "approvedByUsername": null,
  "publishedAt": null,
  "totalPenalty": null
}
```

## Listing

`GET /api/admin/schedule-periods?status=&from=&to=` returns periods ordered by `startDate DESC`, then `id DESC`.

Filters:

- `status`: optional. This batch accepts only `DRAFT`.
- `from`: optional lower bound date.
- `to`: optional upper bound date.
- If both `from` and `to` are provided, `from <= to`.
- Date filters use overlap semantics: a period is included when `period.startDate <= to` and `period.endDate >= from`.

## Authorization

Controller methods use `@PreAuthorize("hasRole('MANAGER')")`.

The service also resolves the current account from `CurrentAccountPrincipal.accountId` and requires the account role to be `MANAGER`. Missing accounts or non-manager accounts return `403 FORBIDDEN`. This keeps the service safe even if a future controller forgets method security.

## Errors

- `400 BAD_REQUEST`: invalid date window, unsupported status, duplicate `(startDate, endDate, versionNo)`, update of a non-`DRAFT` period.
- `403 FORBIDDEN`: authenticated principal is missing, account is missing, or account is not a manager.
- `404 NOT_FOUND`: period does not exist.

## Persistence

Use the existing `SchedulePeriod` entity. Add `SchedulePeriodRepository` with methods for:

- uniqueness check by `startDate`, `endDate`, and `versionNo`;
- uniqueness check excluding the current period during update;
- filtered admin search with optional status/date bounds;
- detail lookup that fetches creator and approver accounts to avoid lazy-loading surprises while mapping responses.

No schema change is required for this batch.

## Testing

Service tests cover:

- manager creates a `DRAFT` period with default version `1`;
- create trims `periodName`;
- create rejects invalid ranges and duplicates;
- list filters by status and date range;
- detail returns mapped creator/approver summary;
- update succeeds for `DRAFT` period;
- update rejects non-`DRAFT`, duplicates, invalid ranges, and missing period;
- non-manager or missing manager account is forbidden.

Controller tests cover:

- manager create/list/detail/update flows;
- request validation returns `400`;
- non-manager access is forbidden;
- anonymous access is unauthorized.

Full backend verification remains `backend/.\mvnw.cmd clean test`.

## Out Of Scope

- WorkShift creation.
- ShiftAssignment manual assignment.
- Assignment lock/unlock.
- Conflict validation for employees and shift overlap.
- Schedule approval or publish.
- AI-service invocation.
- Frontend screens.

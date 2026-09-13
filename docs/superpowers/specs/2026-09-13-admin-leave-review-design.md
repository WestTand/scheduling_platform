# Admin Leave Review APIs Design

## Goal

Implement manager review APIs for employee leave requests so submitted leave can move from `PENDING` to `APPROVED` or `REJECTED` and later feed AI scheduling as approved leave constraints.

This batch covers manager leave review only. Preference review, notifications, audit logs, schedule-period integration, and AI payload generation remain separate follow-up work.

## Scope

### In Scope

- Manager list API for employee leave requests.
- Manager approve API for pending leave requests.
- Manager reject API for pending leave requests.
- Optional review note stored on leave requests.
- Review metadata stored through existing `reviewedBy` and `reviewedAt` fields.
- Validation to prevent approving a leave request that overlaps another approved leave request for the same employee.

### Out of Scope

- Employee notification when a leave request is reviewed.
- Manager review APIs for preferences.
- Audit-log persistence.
- AI scheduling payload generation.
- Schedule-period linkage.

## Existing Context

- `LeaveRequest` already stores `employee`, `startDate`, `endDate`, `reason`, `status`, `reviewedBy`, and `reviewedAt`.
- Employee self-service APIs already create `PENDING` leave requests, list own leave requests, and cancel own pending leave requests.
- Employee leave creation prevents overlap with own `PENDING` or `APPROVED` leave and uses an account-level pessimistic lock before checking overlap.
- Admin controllers already live under `scheduling.scheduling.admin` and use `@PreAuthorize("hasRole('MANAGER')")`.

## API Design

All endpoints require authenticated manager access.

### List Leave Requests

`GET /api/admin/leave-requests?employeeId=&status=&from=&to=`

Behavior:

- Returns leave requests across employees.
- Optional `employeeId` filters exact employee.
- Optional `status` filters exact status.
- Optional `from` and `to` filter leave requests that overlap the provided date window.
- Rejects invalid status values.
- Rejects inverted date filters where `from > to`.
- Sort order: id descending.

Response shape:

```json
[
  {
    "id": 501,
    "employeeId": 7,
    "employeeCode": "E001",
    "employeeName": "Nguyen Van A",
    "startDate": "2026-09-14",
    "endDate": "2026-09-15",
    "reason": "Family appointment",
    "status": "PENDING",
    "reviewNote": null,
    "reviewedByAccountId": null,
    "reviewedByUsername": null,
    "reviewedAt": null
  }
]
```

### Approve Leave Request

`POST /api/admin/leave-requests/{leaveRequestId}/approve`

Request body:

```json
{
  "reviewNote": "Approved for family appointment"
}
```

Behavior:

- Only `PENDING` leave requests can be approved.
- Sets status to `APPROVED`.
- Sets `reviewedBy` to the current manager account.
- Sets `reviewedAt` to the current server time.
- Stores optional trimmed `reviewNote`; blank note is stored as `null`.
- Rejects approval when another leave request for the same employee is already `APPROVED` and overlaps the request date range.
- Uses a locked leave-request lookup before checking status and updating review fields.

### Reject Leave Request

`POST /api/admin/leave-requests/{leaveRequestId}/reject`

Request body:

```json
{
  "reviewNote": "Team coverage is not enough"
}
```

Behavior:

- Only `PENDING` leave requests can be rejected.
- Sets status to `REJECTED`.
- Sets `reviewedBy` to the current manager account.
- Sets `reviewedAt` to the current server time.
- Stores optional trimmed `reviewNote`; blank note is stored as `null`.
- Uses a locked leave-request lookup before checking status and updating review fields.

## Data Model

Add nullable review note support to `LeaveRequest`:

- Java field: `reviewNote`
- Database column: `review_note TEXT NULL`

Existing fields continue to be used:

- `reviewedBy`: manager account that approved or rejected the request.
- `reviewedAt`: server timestamp when the review action was applied.

## Validation Rules

Accepted statuses for list filters:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Review actions:

- Approve and reject require the leave request to exist.
- Approve and reject require current status `PENDING`.
- Approve and reject resolve the current manager account from `CurrentAccountPrincipal.accountId`; missing or non-manager accounts return forbidden.
- Approve rejects overlap with another `APPROVED` leave request for the same employee.
- The overlap check excludes the leave request being approved.
- `reviewNote` max length is `1000` characters.

## Components

- `AdminLeaveRequestController`: exposes manager list/approve/reject endpoints.
- `AdminLeaveRequestService`: resolves current manager account, validates filters and review actions, sets review metadata.
- `AdminLeaveRequestResponse`: response DTO with employee summary and review metadata.
- `ReviewLeaveRequestRequest`: approve/reject request DTO with optional `reviewNote`.
- `LeaveRequestRepository`: extend with manager list query, locked lookup, and approved-overlap check excluding a request id.

The implementation should keep manager review logic separate from employee self-service services. Employee APIs own employee submission/cancellation; admin APIs own review transitions.

## Error Handling

- Invalid filters, invalid status transitions, and approved-overlap conflicts return `400 BAD_REQUEST`.
- Missing leave request returns `404 NOT_FOUND`.
- Non-manager access returns `403 FORBIDDEN` through method security.
- Unauthenticated access remains `401 UNAUTHORIZED` through the security filter.

## Testing Strategy

### Service Tests

- List normalizes status filters and validates date window.
- Approve updates status, review note, reviewedBy, and reviewedAt.
- Approve rejects non-pending leave requests.
- Approve rejects overlap with another approved leave request for the same employee.
- Reject updates status, review note, reviewedBy, and reviewedAt.
- Reject rejects non-pending leave requests.
- Blank review note is stored as `null`.

### Controller Tests

- Manager can list leave requests with filters.
- Manager can approve a leave request and request body binds correctly.
- Manager can reject a leave request and request body binds correctly.
- Employee cannot access admin leave review APIs.
- Invalid request body returns `400` before service calls.

### Regression

- Run targeted admin leave review tests.
- Run full backend `.\mvnw.cmd clean test`.

## Follow-Up Work

- Employee response can expose review note and review metadata if frontend needs it.
- Notifications for approved/rejected leave requests.
- Manager preference visibility or review policy.
- AI scheduling payload generation for approved leave requests.

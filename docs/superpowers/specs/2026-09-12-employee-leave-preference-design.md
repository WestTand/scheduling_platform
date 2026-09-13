# Employee Leave and Preference APIs Design

## Goal

Implement the first Phase 4 employee self-service slice: employees can submit, list, update, and cancel scheduling inputs that later feed manager review and AI scheduling.

This batch covers only employee-owned leave requests and preferences. Manager approval/rejection APIs, schedule-period modeling, swap requests, and personal schedule viewing remain separate follow-up batches.

## Scope

### In Scope

- Employee leave request APIs:
  - List own leave requests.
  - Create a leave request.
  - Cancel an own pending leave request.
- Employee preference APIs:
  - List own preferences.
  - Create a preference.
  - Update an own pending preference.
  - Cancel an own pending preference.
- Service validation, repository queries, DTOs, and tests for the above.
- Status convention support for `CANCELLED` on `LeaveRequest` and `Preference`.

### Out of Scope

- Manager approval/rejection APIs.
- Schedule period linkage.
- Swap request APIs.
- Employee personal schedule viewing.
- AI scheduling payload generation.

## Existing Context

- `LeaveRequest` already exists with `employee`, `startDate`, `endDate`, `reason`, `status`, `reviewedBy`, and `reviewedAt`.
- `Preference` already exists with `employee`, optional `shiftType`, `preferenceDate`, `preferenceType`, `priority`, `note`, and `status`.
- `Account` links to `Employee`; authenticated requests expose `CurrentAccountPrincipal` via Spring Security.
- Current backend style uses controller/service/repository layers, record DTOs, `ResponseStatusException` for invalid admin requests, and focused controller/service tests.

## API Design

All endpoints are authenticated and intended for `ROLE_EMPLOYEE` accounts.

### Leave Requests

`GET /api/employee/leave-requests?status=&from=&to=`

- Returns only leave requests owned by the current employee.
- Optional `status` filters exact status.
- Optional `from` and `to` filter requests that overlap the provided date window.
- Sort order: id descending.

`POST /api/employee/leave-requests`

Request:

```json
{
  "startDate": "2026-09-14",
  "endDate": "2026-09-15",
  "reason": "Family appointment"
}
```

Behavior:

- Creates a `PENDING` leave request for the current employee.
- Rejects when `endDate < startDate`.
- Rejects when the requested date range overlaps any existing own leave request with status `PENDING` or `APPROVED`.

`POST /api/employee/leave-requests/{leaveRequestId}/cancel`

- Cancels only an own leave request.
- Only `PENDING` requests can be cancelled.
- Sets status to `CANCELLED`; records are not deleted.

### Preferences

`GET /api/employee/preferences?status=&from=&to=`

- Returns only preferences owned by the current employee.
- Optional `status` filters exact status.
- Optional `from` and `to` filter `preferenceDate` inclusively.
- Sort order: preference date ascending, then id descending.

`POST /api/employee/preferences`

Request:

```json
{
  "preferenceDate": "2026-09-14",
  "preferenceType": "WANT",
  "shiftTypeId": 10,
  "priority": 1,
  "note": "Prefer morning shift"
}
```

Behavior:

- Creates a `PENDING` preference for the current employee.
- `preferenceType` must be `WANT` or `AVOID`.
- `shiftTypeId` is optional. When present, the shift type must exist and be active.
- `priority` must be at least `1`.
- Rejects duplicates for the same employee, `preferenceDate`, `shiftTypeId`, and `preferenceType` when an existing record has status `PENDING` or `APPROVED`.
- A `null` `shiftTypeId` means the preference applies to the whole day.

`PUT /api/employee/preferences/{preferenceId}`

- Updates only an own `PENDING` preference.
- Applies the same validation as create.
- Duplicate checks ignore the preference being updated.

`POST /api/employee/preferences/{preferenceId}/cancel`

- Cancels only an own preference.
- Only `PENDING` preferences can be cancelled.
- Sets status to `CANCELLED`; records are not deleted.

## Data and Validation Rules

### Current Employee Resolution

Services resolve the current employee from `CurrentAccountPrincipal.accountId` through `AccountRepository`.

- If the account does not exist or is not an employee account, return forbidden.
- If the account has no linked employee, return not found.
- If the linked employee is inactive, return forbidden.

### Status Values

Leave requests use:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Preferences use:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Manager review can later transition `PENDING` to `APPROVED` or `REJECTED`. Employee APIs only create `PENDING` and cancel `PENDING` to `CANCELLED`.

### Date Filtering

For leave requests, date-window filtering should include any request that overlaps the provided range:

- `endDate >= from` when `from` is present.
- `startDate <= to` when `to` is present.

For preferences, date-window filtering applies directly to `preferenceDate`:

- `preferenceDate >= from` when `from` is present.
- `preferenceDate <= to` when `to` is present.

## Components

- `EmployeeLeaveRequestController`: exposes leave endpoints.
- `EmployeeLeaveRequestService`: handles employee resolution, validation, ownership checks, and persistence.
- `EmployeePreferenceController`: exposes preference endpoints.
- `EmployeePreferenceService`: handles employee resolution, validation, duplicate checks, shift type lookup, ownership checks, and persistence.
- `LeaveRequestRepository`: list, overlap detection, and lookup by id/employee.
- `PreferenceRepository`: list, duplicate detection, and lookup by id/employee.
- DTOs under an employee package for request/response shapes.

The implementation should keep leave and preference services separate. They share concepts, but their validation and date semantics differ enough that a shared abstraction would add unnecessary indirection.

## Error Handling

- Invalid date ranges, invalid statuses, duplicate preferences, overlapping leave requests, invalid preference types, and invalid pending-only operations return `400 BAD_REQUEST`.
- Missing owned records return `404 NOT_FOUND`.
- Non-employee or inactive employee access returns `403 FORBIDDEN`.
- Existing authentication failures continue to return `401 UNAUTHORIZED` through the security filter.

## Testing Strategy

### Service Tests

- Leave create defaults to `PENDING`.
- Leave create rejects `endDate < startDate`.
- Leave create rejects overlap with own `PENDING` or `APPROVED` leave.
- Leave create allows non-overlap and ignores `REJECTED`/`CANCELLED` overlaps.
- Leave cancel only works for own `PENDING` request.
- Preference create accepts `WANT` and `AVOID`.
- Preference create supports null `shiftTypeId`.
- Preference create rejects invalid type, inactive/missing shift type, and duplicate active preference key.
- Preference update only works for own `PENDING` preference.
- Preference cancel only works for own `PENDING` preference.
- Current employee resolution rejects invalid account states.

### Controller Tests

- Employee can call each endpoint and request bodies bind correctly.
- Manager cannot call employee-only endpoints.
- Invalid request bodies return `400` before service calls.
- Responses include ownership-safe employee input data and omit unrelated account internals.

### Regression

- Run targeted employee leave/preference tests.
- Run full backend `./mvnw clean test` equivalent on Windows: `.\mvnw.cmd clean test`.

## Follow-Up Work

- Manager review APIs for leave requests.
- Manager preference visibility/review policy.
- Phase 5 schedule period and assignment APIs.
- AI payload generation that includes only approved leave requests and applicable preferences.

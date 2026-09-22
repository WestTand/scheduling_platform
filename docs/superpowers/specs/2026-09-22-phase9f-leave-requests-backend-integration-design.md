# Phase 9F Leave Requests Backend Integration Design

## Context

Phase 9C connected manager employee/account management to the backend. Phase 9D connected manager shift types. Phase 9E added manager scheduling rules. The next scheduling input to connect is leave requests because approved leave is a hard constraint for scheduling.

The backend already exposes leave request APIs for both roles:

- Manager review APIs under `/api/admin/leave-requests`.
- Employee self-service APIs under `/api/employee/leave-requests`.

The frontend currently uses mock/local state for both manager and employee leave request pages.

## Goal

Connect leave request flows end-to-end:

1. Employees can list, create, and cancel their leave requests against the backend.
2. Managers can list, filter, approve, and reject employee leave requests against the backend.
3. Mock mode remains available for frontend-only development.

## Scope

Included:

- Add backend DTO types for manager and employee leave APIs.
- Add adapter functions from backend DTOs to existing frontend `LeaveRequest` UI model.
- Add backend leave request service for manager and employee endpoints.
- Update public `leaveRequestService` to switch between mock and backend mode.
- Update manager `LeaveRequestsPage` to keep current UI but use backend-aware service.
- Update employee `EmployeeLeaveRequestsPage` to use async service, loading/error states, create, and cancel.
- Add support for backend status `CANCELLED` in UI where required.
- Verify backend-mode API access and frontend builds.

Excluded:

- No backend contract redesign unless a verified bug blocks the integration.
- No scheduling generation or AI changes in this slice.
- No notification/activity-log frontend integration in this slice.
- No leave-balance/annual-quota feature.

## Backend Contract

Manager APIs:

- `GET /api/admin/leave-requests?employeeId=&status=&from=&to=` returns `AdminLeaveRequestResponse[]`.
- `POST /api/admin/leave-requests/{leaveRequestId}/approve` with optional body `{ reviewNote?: string }`.
- `POST /api/admin/leave-requests/{leaveRequestId}/reject` with optional body `{ reviewNote?: string }`.

Manager response fields:

```ts
interface BackendAdminLeaveRequestResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  startDate: string
  endDate: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  reviewNote: string | null
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
}
```

Employee APIs:

- `GET /api/employee/leave-requests?status=&from=&to=` returns `EmployeeLeaveRequestResponse[]`.
- `POST /api/employee/leave-requests` with `{ startDate, endDate, reason }`.
- `POST /api/employee/leave-requests/{leaveRequestId}/cancel` returns no content.

Employee response fields:

```ts
interface BackendEmployeeLeaveRequestResponse {
  id: number
  startDate: string
  endDate: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
}
```

## Frontend Model Mapping

The existing frontend `LeaveRequest` model will remain the page-facing model.

Manager response mapping:

- `id`: stringified backend id.
- `employeeName`: backend `employeeName`.
- `employeeEmail`: empty string because backend response does not include email.
- `startDate`, `endDate`, `reason`, `status`: copied from backend.
- `totalDays`: computed inclusively from `startDate` to `endDate`.
- `requestedAt`: fallback to `startDate` because backend response currently does not expose creation timestamp.
- `reviewedBy`: backend `reviewedByUsername` when present.
- `reviewedAt`: backend `reviewedAt`.
- `managerNote`: backend `reviewNote` when present.

Employee response mapping:

- `id`: stringified backend id.
- `employeeName`: current user name is not available in the DTO; display `Bạn` or omit where the employee page does not need it.
- `employeeEmail`: empty string.
- `startDate`, `endDate`, `reason`, `status`: copied from backend.
- `totalDays`: computed inclusively.
- `requestedAt`: fallback to `startDate`.
- `managerNote`: status-based display text for employee page, with no review note available from the employee DTO.

## UI Design

Manager page stays structurally the same:

- Filters: search, status, start date, end date.
- Table: request id, employee, dates, total days, status, actions.
- Detail dialog.
- Approve/reject dialog with optional manager note; rejection still requires a note in the frontend.

Employee page becomes service-backed:

- Load list on mount and when filters change.
- Show loading/error/empty states.
- Create dialog submits `{ startDate, endDate, reason }` to backend; leave type/note remain frontend-only labels or are removed from backend payload.
- Cancel button calls backend cancel only for `PENDING` requests, then reloads list.
- `CANCELLED` status displays as cancelled/hủy if supported by badge copy.

## Architecture

Use the established Phase 9 integration pattern:

- Backend DTO types live in `frontend/src/types/backendLeaveRequest.ts`.
- Adapters live in `frontend/src/adapters/leaveRequestAdapter.ts`.
- Backend calls live in `frontend/src/services/backendLeaveRequestService.ts`.
- Public `leaveRequestService` keeps current method names where possible and routes mock/backend by `apiConfig.useMockApi`.
- React pages consume only existing frontend types from `frontend/src/types/management.ts`.

## Data Flow

Employee create flow:

1. Employee fills leave request form.
2. Page validates date range and reason.
3. `leaveRequestService.createEmployeeLeaveRequest` sends backend request in backend mode.
4. Page reloads the employee list and shows success feedback.

Manager review flow:

1. Manager opens pending leave request.
2. Manager approves or rejects.
3. `leaveRequestService.updateStatus` calls backend approve/reject endpoint in backend mode.
4. Page reloads manager list.

Cancel flow:

1. Employee clicks cancel on a pending request.
2. `leaveRequestService.cancelEmployeeLeaveRequest` calls backend cancel endpoint in backend mode.
3. Page reloads employee list and shows cancelled status if returned by future list call.

## Error Handling

- Backend validation errors surface through `apiClient` messages.
- Failed manager review keeps dialog open and shows/alerts the error using the current page pattern.
- Failed employee create/cancel keeps current user input where practical.
- Date range validation runs client-side before submit.

## Testing

- Frontend default build must pass.
- Frontend backend-mode build must pass.
- Backend focused tests only if backend code must change.
- Browser smoke in backend mode:
  - Login as employee.
  - Open `/employee/leave-requests`.
  - Create a pending leave request.
  - Cancel a pending request.
  - Login as manager.
  - Open `/manager/leave-requests`.
  - Confirm the created pending request appears if not cancelled.
  - Approve or reject a pending request.

## Acceptance Criteria

- Employee leave page works in mock mode and backend mode.
- Manager leave review page works in mock mode and backend mode.
- Existing UI pages do not map raw backend DTOs directly.
- `CANCELLED` status does not break tables or badges.
- No schedule generation, assignment, AI, notification, or activity-log UI is changed in this slice.

## Spec Self-Review

- Placeholder scan: no placeholders remain.
- Internal consistency: scope, architecture, and acceptance criteria all target end-to-end leave request integration.
- Scope check: focused enough for one implementation plan.
- Ambiguity check: lack of email, creation timestamp, and employee review note in backend DTOs is handled explicitly by frontend mapping.

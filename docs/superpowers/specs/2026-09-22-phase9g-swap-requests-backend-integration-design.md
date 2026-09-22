# Phase 9G Swap Requests Backend Integration Design

## Goal

Connect swap requests end-to-end between the frontend and the existing backend APIs. Employees should be able to create and manage real swap requests from their published schedule assignments, and managers should be able to review those same requests from the manager UI.

## Scope

This slice includes:

- Employee list of real swap requests from `GET /api/employee/swap-requests`.
- Employee create flow using a real assignment picker backed by `GET /api/employee/schedule`.
- Employee cancellation of pending requests through `POST /api/employee/swap-requests/{id}/cancel`.
- Employee response to incoming swap requests through `POST /api/employee/swap-requests/{id}/respond` when the current account is the target employee.
- Manager list of real swap requests from `GET /api/admin/swap-requests`.
- Manager approve/reject actions through `POST /api/admin/swap-requests/{id}/approve` and `POST /api/admin/swap-requests/{id}/reject`.
- Frontend mock mode remains available.
- Live verification against the local backend after implementation.

This slice does not include:

- Mutating the actual schedule assignment after approval. The backend currently models status transitions only.
- A full calendar-style swap creation wizard.
- Bulk review, exports, or notifications UI changes.

## Current State

The backend already exposes swap request APIs:

- `GET /api/employee/swap-requests?status=...`
- `POST /api/employee/swap-requests`
- `POST /api/employee/swap-requests/{id}/cancel`
- `POST /api/employee/swap-requests/{id}/respond`
- `GET /api/admin/swap-requests?status=...`
- `POST /api/admin/swap-requests/{id}/approve`
- `POST /api/admin/swap-requests/{id}/reject`

The frontend currently has:

- `SwapRequestsPage` using `swapRequestService`, which is mock-only today.
- `EmployeeSwapRequestsPage` using local seed state and a fake shift-code-based create form.
- `GET /api/employee/schedule`, which returns employee assignment IDs and shift details needed for a real requester assignment picker.

## Design

### Frontend Service Boundary

Add backend-aware swap request services mirroring the leave-request pattern:

- `frontend/src/types/backendSwapRequest.ts` for backend DTOs.
- `frontend/src/adapters/swapRequestAdapter.ts` to map `SwapRequestResponse` into the existing `SwapRequest` view model.
- `frontend/src/services/backendSwapRequestService.ts` for backend HTTP calls.
- Update `frontend/src/services/swapRequestService.ts` to route to backend when `VITE_USE_MOCK_API=false`.

The public `swapRequestService` should expose methods used by both pages:

- `list(params)` for manager list.
- `listMine(params)` for employee list.
- `createMine(input)` for employee create.
- `cancelMine(id)` for employee cancel.
- `respondMine(id, accept)` for target employee response.
- `updateStatus(id, status)` for manager approve/reject.

Manager note is not sent for swap review because the backend approve/reject endpoints currently do not accept a request body.

### Assignment Picker

The employee create form must use backend assignment IDs instead of mock shift codes.

Requester assignment options come from `GET /api/employee/schedule` and should display:

- Work date.
- Shift name.
- Start/end time.
- Period name/status.

Only assignments from published periods are valid according to backend rules. The frontend can show all returned assignments but should prefer a validation message before submit when the selected period is not `PUBLISHED`. Backend remains the source of truth.

### Target Employee Selection

The create request needs `targetEmployeeId`. Employees should not call manager-only employee APIs.

Add a minimal employee-safe coworkers endpoint because the current employee UI has no employee-accessible active coworker API:

- `GET /api/employee/coworkers`
- Returns active employees only.
- Excludes the current employee.
- Response contains only `id`, `employeeCode`, and `fullName`.

The frontend uses this endpoint to populate the target employee select.

### Target Assignment

`targetAssignmentId` is optional in `CreateSwapRequestRequest`. For this slice, the create form will not include a target assignment picker and will always send `targetAssignmentId: null`.

This keeps the first end-to-end swap slice small while still using real requester assignments and real target employees. A later UX slice can add target assignment lookup by employee/date.

### Employee Page Behavior

`EmployeeSwapRequestsPage` will switch from local seed state to service-backed state:

- Load with loading/error/empty states.
- Filter by status using backend `status` when not `ALL`.
- Search locally by reason, target employee, requester employee, shift name, or status.
- Show details for selected request.
- Show cancel only for requests created by the current employee with status `PENDING_TARGET`.
- Show accept/reject response actions only when the target employee is the current user and status is `PENDING_TARGET`.

After create, cancel, or respond, reload from backend.

### Manager Page Behavior

`SwapRequestsPage` will keep the existing layout and replace mock data with backend data:

- Load with `swapRequestService.list`.
- Send status to backend when not `ALL`.
- Keep search/date filters local because backend currently supports status only.
- Approve enabled only for `PENDING_APPROVAL` requests.
- Reject enabled for `PENDING_TARGET` and `PENDING_APPROVAL` requests, matching backend review rules.
- Remove copy that mentions mock state.
- After approve/reject, reload from backend.

### Status Mapping

The adapter preserves backend statuses in the frontend type because swap requests have two meaningful pending states:

- `PENDING_TARGET` means waiting for the target employee.
- `PENDING_APPROVAL` means waiting for manager approval.
- `APPROVED`, `REJECTED`, and `CANCELLED` map directly.
- `StatusBadge` should display both pending statuses with clear Vietnamese labels.

Frontend `SwapRequestStatus` should be extended deliberately rather than collapsing backend statuses into generic `PENDING`.

### Error Handling

- Reuse `apiClient` auth refresh behavior.
- Surface backend error messages in page-level error states or action alerts.
- Do not silently mutate local state on backend action success; always reload from backend.
- If coworker or schedule loading fails, disable create submit and show an actionable error.

## Testing And Verification

Automated verification:

- Frontend adapter tests for mapping backend swap request responses.
- Frontend service tests for create, cancel, respond, approve, and reject endpoint paths using the existing Vitest setup.
- Backend tests only for any new coworker endpoint.

Manual/live verification:

- Login as employee and load swap requests.
- Load create form and confirm requester assignments come from `/api/employee/schedule`.
- Create a swap request with a real assignment and target employee.
- Cancel one pending request.
- If a target employee account is available, login as target and accept/reject an incoming request.
- Login as manager and reject one request.
- Create another request and approve it as manager.
- Confirm manager and employee lists show updated backend statuses after reload.

## Risks

- Employee test data may not have published schedule assignments, so live create verification may require creating/publishing a schedule first.
- Target employee response UX depends on whether the backend response exposes enough information to identify incoming requests for the current user.
- Adding `GET /api/employee/coworkers` is a small backend addition but should stay intentionally narrow to avoid exposing manager-only employee data.

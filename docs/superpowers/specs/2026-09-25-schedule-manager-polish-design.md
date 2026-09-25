# Schedule Manager Polish Design

**Goal:** Complete the manager schedule creation/listing UX, employee schedule backend rendering, and auth-token reliability so the app can be tested end-to-end without raw technical errors.

**Scope:** This spec covers backend schedule-generation validation, schedule list filtering, frontend pagination/defaults/messages, employee schedule backend mode, and token refresh behavior. It does not redesign scheduling rules or the AI solver.

## Backend Behavior

### Schedule Generation Duplicate Handling

Current behavior blocks any duplicate `(startDate, endDate, versionNo)`, including cancelled periods. New behavior:

- If an existing period with the same `(startDate, endDate, versionNo)` has status `CANCELLED`, creation is allowed.
- If any matching period has status other than `CANCELLED`, creation is rejected.
- The rejection response must contain an end-user-readable message, not only `Invalid schedule generation request`.
- The frontend should be able to show: `Đã tồn tại lịch trong khoảng ngày này. Vui lòng chọn lịch đã có hoặc đổi ngày.`

### Error Contract

Backend errors used by frontend screens should expose a stable JSON shape compatible with the current API client. If the existing global error handler already has a shape, reuse it. If a `ResponseStatusException` is currently rendered as generic text, add/adjust handling so frontend can extract `message` reliably.

Expected frontend behavior:

- 400 duplicate period: show Vietnamese user message.
- Other generation failures: show `Không thể tạo lịch. Vui lòng kiểm tra dữ liệu và thử lại.` unless backend provides a better message.

### Schedule List Filtering

The manager schedule list endpoint must support date filters without returning 500:

- `startDate`/`endDate` from frontend should map to backend-supported query params.
- If backend expects `from`/`to`, frontend should send those names.
- Invalid date ranges should produce a 400 with readable message instead of a 500.
- The query should return periods overlapping or within the requested range according to existing backend semantics; do not change semantics unless tests reveal they are broken.

### Created Time

The manager schedule list should render a real created timestamp. If backend period DTO does not expose `createdAt`, add it from the entity base timestamp. Frontend should use `createdAt` for display. `publishedAt` should remain only publication time.

## Frontend Manager UX

### `/manager/schedule-generation`

- Default start date is the browser's current local date.
- Default end date follows the existing default period type:
  - If default period is `WEEK`, end date is start date plus 6 days.
  - If period type changes later, preserve existing form behavior as much as possible.
- Employee pool selection is paginated client-side to avoid a long page. Default page size: 9 employees per page.
- Existing search should filter the employee list before pagination.
- Select all behavior should apply to all active employees matching the current source list, not only current page, unless the current UI label explicitly says page-only. Prefer keeping existing all-active semantics.
- Generation notices should be end-user-readable:
  - Success: `Tạo lịch thành công.`
  - Infeasible: `Đã tạo lịch nhưng chưa phân công đủ. Vui lòng xem cảnh báo hoặc chỉnh sửa lịch.`
  - Failed: `Không thể tạo lịch. Vui lòng kiểm tra dữ liệu và thử lại.`
- Do not display raw `AI: SUCCESS`, `Solver: OPTIMAL`, exception class names, or raw HTTP codes in the main notice.

### `/manager/schedules`

- Add client-side pagination for schedule cards/table. Default page size: 10 periods per page.
- Pagination should reset to page 1 when filters/search/status change.
- Empty state should still appear when no schedules match.
- Date filters should call backend with the correct query parameter names and should not produce 500 for valid date ranges.
- Display created time from `createdAt`. If `createdAt` is missing for older responses, fallback to start date to avoid crashing.

## Employee Schedule

### `/employee/schedule`

Current page uses mock schedule data. New behavior:

- In backend mode (`VITE_USE_MOCK_API=false`), fetch the authenticated employee's schedule from backend.
- Use existing employee schedule API if available. If the API needs a date window, default to the current week.
- Render shift name, date, time range, status, and warnings from backend data.
- If no assignments exist, show a clear empty state: `Bạn chưa có lịch làm việc trong khoảng thời gian này.`
- In mock mode, keep existing mock rendering.

## Auth Token Reliability

Expected flow:

- Authenticated API request sends current access token.
- If backend returns 401, frontend calls `/api/auth/refresh` with credentials included.
- If refresh succeeds, store the new access token in the same storage scope as the current session and retry the original request once.
- If refresh fails, clear stored auth and route user to login or unauthenticated state.

Implementation constraints:

- Avoid infinite refresh loops.
- Do not clear auth on non-auth errors such as 400/403/500.
- Preserve remember-me behavior: local storage sessions stay local; non-remember sessions stay session storage.
- Confirm refresh cookie settings match local dev CORS: credentials included, allowed origin exact, and backend allows credentials.

## Testing Plan

Backend tests:

- Schedule generation allows duplicate date/version when existing period is `CANCELLED`.
- Schedule generation rejects duplicate date/version when existing period is not `CANCELLED` and returns readable message.
- Schedule list date filtering with valid dates does not return 500.
- Invalid schedule list date range returns 400.
- CORS preflight allows `PATCH` for admin schedule actions. This regression already exists and should remain.

Frontend tests:

- Schedule generation default date equals today's local date.
- Generation notices hide raw AI/solver/code details and show user-facing Vietnamese copy.
- Employee pool pagination works with search.
- Manager schedule list paginates and resets page on filters.
- Schedule list sends backend date filter params correctly.
- Employee schedule uses backend service in backend mode and mock data only in mock mode.
- API client refreshes expired access token, retries once, and preserves storage scope.

## Rollout Notes

- This work should be implemented in focused slices but can ship as one batch.
- Prefer client-side pagination for manager list and employee pool because current endpoints return arrays and the dataset is small in this project.
- Do not change AI solver rules in this batch.
- Do not commit unrelated untracked docs unless explicitly requested.

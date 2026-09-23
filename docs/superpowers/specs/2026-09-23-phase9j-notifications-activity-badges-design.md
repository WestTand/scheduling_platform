# Phase 9J Notifications, Activity Logs, and Real Badges Design

## Context

Phase 9D through Phase 9I moved the main manager and employee workflows from mock data to backend APIs. Notifications, activity logs, and sidebar badges still rely on mock state or hardcoded backend-mode suppression.

The backend already has notification and activity-log infrastructure:

- `GET /api/employee/notifications`
- `PATCH /api/employee/notifications/{notificationId}/read`
- `PATCH /api/employee/notifications/read-all`
- `GET /api/admin/activity-logs`

The current frontend gaps are:

- Manager notifications page uses `notificationService`, but that service is mock-only.
- Employee notifications page uses a local hardcoded array.
- Manager activity logs page uses `activityLogService`, but that service is mock-only.
- Sidebar badges return `0` in backend mode to avoid showing mock counts, so real pending counts are hidden.
- Manager notification page includes a mock-only manual create-notification form that does not have a backend endpoint.

## Goals

- Connect manager and employee notification pages to backend data in backend mode.
- Connect manager activity logs page to backend data in backend mode.
- Replace backend-mode mock badge suppression with real backend summary counts.
- Keep mock mode behavior intact for local/demo use.
- Hide the manual create-notification UI in backend mode until a real backend create endpoint exists.

## Non-Goals

- No manual create-notification backend endpoint in this slice.
- No notification recipient selection or broadcast workflow.
- No server-side pagination changes for existing notification/activity endpoints.
- No realtime push/websocket notification updates.
- No broad dashboard-count redesign beyond Sidebar badges.

## Backend Design

### Navigation Summary Endpoint

Add a small authenticated summary endpoint, proposed path:

`GET /api/me/navigation-summary`

The endpoint returns role-aware counts for the currently authenticated account:

```json
{
  "pendingLeaveRequests": 0,
  "pendingPreferences": 0,
  "pendingSwapRequests": 0,
  "unreadNotifications": 0,
  "pendingSchedules": 0
}
```

Manager counts:

- `pendingLeaveRequests`: leave requests with `PENDING`.
- `pendingPreferences`: preferences with `PENDING`.
- `pendingSwapRequests`: swap requests needing manager approval, normally `PENDING_APPROVAL`.
- `unreadNotifications`: notifications for the manager account with `read=false`.
- `pendingSchedules`: schedule periods with `PENDING_APPROVAL` when the existing repository exposes this status; if no current schedule-period status uses `PENDING_APPROVAL`, return `0` for this slice and cover that behavior in tests.

Employee counts:

- `pendingLeaveRequests`: own leave requests with `PENDING`.
- `pendingPreferences`: own preferences with `PENDING`.
- `pendingSwapRequests`: swap requests waiting on the employee where applicable, such as target approval.
- `unreadNotifications`: notifications for the employee account with `read=false`.
- `pendingSchedules`: `0`.

The service should use repository count queries instead of loading full lists. If a count does not have an existing repository method, add the smallest targeted count query.

### Existing Notification Endpoint

Reuse `/api/employee/notifications` for both manager and employee users. The backend already permits `EMPLOYEE`, `MANAGER`, and `ADMIN`, and the endpoint returns notifications for the current recipient account.

No backend changes are required for notification list/read/read-all unless tests reveal missing null guards or count helpers.

### Existing Activity Logs Endpoint

Reuse `/api/admin/activity-logs` for manager activity log display. Existing filters remain:

- `action`
- `entityType`
- `accountId`
- `from`
- `to`

Frontend Phase 9J will only send the filters currently supported by the UI: action/date range. Search remains client-side over the returned list.

## Frontend Design

### Backend Notification Adapter and Service

Add backend notification types and adapter:

- `id` -> string id
- `title` -> title
- `content` -> content
- `notificationType` -> UI `NotificationType`
- unknown notification types -> `GENERAL`
- `read` -> `isRead`
- `createdAt` -> createdAt
- `priority` -> `MEDIUM` because backend does not currently expose priority
- `recipient` -> `Bạn`
- `recipientType` -> `SELECTED`

Add `backendNotificationService` methods:

- `list({ page, pageSize, search, type, status })`
- `markRead(id)`
- `markAllRead()`

The service calls existing backend endpoints and performs client-side pagination/filtering to match current UI behavior.

Update `notificationService` facade:

- mock mode keeps existing seed-backed behavior.
- backend mode delegates to `backendNotificationService`.
- `create()` remains mock-only. In backend mode, pages should not call it because create UI is hidden.

### Manager Notifications Page

Use the existing async loading/error flow. In backend mode:

- show real notification rows.
- keep search/type/read-status filters.
- allow mark one read and mark all read.
- hide the entire manual “Tạo thông báo” panel and any mock success message for creating notifications.
- update page description so it does not claim mock-state behavior in backend mode.

In mock mode:

- keep the existing create panel available with the current seed-backed behavior.

### Employee Notifications Page

Replace local hardcoded notification array with `notificationService`.

Add the same production-grade states already used by other backend-integrated pages:

- loading
- error with retry
- empty state
- paginated result if needed, or page size large enough to preserve the simple page behavior

Keep URL behavior:

- `status` query param controls read/unread filter.
- `notificationId` opens the detail dialog when present and the notification is loaded.

Mark-read actions should refresh the list and update the detail state.

### Backend Activity Log Adapter and Service

Add backend activity log types and adapter:

- `id` -> string id
- `createdAt` -> `timestamp`
- `username` -> `actorName`
- `actorRole` -> `MANAGER` by default unless future backend response exposes role
- `action` -> action
- `entityType + entityId` -> target, for example `leave_requests #5`
- `oldValueJson/newValueJson` -> concise description, preferring `newValueJson` when present
- `status` -> `SUCCESS`
- `ipAddress` -> ipAddress or empty string

Add `backendActivityLogService.list()` that calls `/api/admin/activity-logs`, maps filters, and applies client-side search/pagination.

Update `activityLogService` facade:

- mock mode keeps seed behavior.
- backend mode delegates to `backendActivityLogService`.

### Sidebar Real Badges

Add backend navigation summary types/service:

- `backendNavigationSummaryService.get()` calls `/api/me/navigation-summary`.
- `navigationSummaryService.get()` returns mock-derived counts in mock mode and backend counts in backend mode.

Update `Sidebar` so it no longer reads mock seeds directly in backend mode. Required behavior:

- `Sidebar` loads summary counts once on mount and when user role changes.
- If the request is loading or fails, show no badge rather than showing stale mock values.
- Mock mode may keep current seed-derived badge behavior through the summary facade.

Badge mapping:

- `Ngày nghỉ` -> `pendingLeaveRequests`
- `Nguyện vọng` -> `pendingPreferences`
- `Yêu cầu đổi ca` -> `pendingSwapRequests`
- `Thông báo` -> `unreadNotifications`
- manager `Quản lý lịch` -> `pendingSchedules`

This fixes the current regression where backend-mode badges always return `0` even when pending backend data exists.

## Error Handling

- Notification and activity pages should display `ErrorState` on backend failures and preserve existing retry buttons.
- Sidebar summary failure should be non-blocking; render no badges and keep navigation usable.
- Unknown backend notification types map to `GENERAL` to avoid crashing UI.
- Activity-log JSON snapshots should be displayed as compact text, not parsed into fragile UI in this slice.

## Testing Plan

Backend tests:

- summary service/controller returns unread notification count for current account.
- manager summary includes pending leave/preference/swap counts.
- employee summary scopes counts to the current employee/account.
- role authorization follows existing authenticated principal rules.

Frontend tests:

- notification adapter maps backend response to `NotificationItem` and falls back unknown type to `GENERAL`.
- backend notification service sends list/read/read-all requests to the expected endpoints.
- activity-log adapter maps backend response to `ActivityLog`.
- backend activity-log service maps filters to `/api/admin/activity-logs` query params.
- sidebar renders backend summary badges when counts are positive.
- sidebar does not render mock badges in backend mode while summary is unavailable or zero.
- manager notification page does not render the create-notification panel in backend mode.

Verification commands:

- Backend focused tests for summary/notification/activity controllers and services.
- `./mvnw.cmd test`
- Frontend focused Vitest tests for adapters/services/sidebar/pages.
- `npm.cmd test`
- `npm.cmd run lint`
- `npm.cmd run build`

## Rollout Notes

- Existing backend notification rows become visible immediately after frontend wiring.
- Existing activity log rows become visible on manager activity log page.
- Existing sidebar badge behavior in mock mode remains unchanged.
- Backend mode no longer shows mock counts; it shows real counts when the summary endpoint succeeds.
- Manual notification creation is intentionally hidden in backend mode until a future slice adds a real backend authoring workflow.

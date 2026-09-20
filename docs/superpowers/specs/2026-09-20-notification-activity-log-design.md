# Phase 7 Notification & Activity Log Design

## Scope

Build Phase 7 as a backend-first feature. The backend will persist in-app notifications and activity logs, expose read APIs for users/managers, and wire notification/log creation into the existing backend business flows. Frontend integration remains out of scope for this phase and will be handled in Phase 9.

This phase does not add email, websocket, push notification, realtime delivery, or export/report features.

## Goals

- Give users an in-app notification inbox backed by the `notifications` table.
- Give managers an audit trail backed by the `activity_logs` table.
- Record important backend actions without duplicating persistence logic across services.
- Keep the design small enough to fit existing service/controller patterns.

## Existing Foundation

The entities already exist:

- `Notification`: recipient account, title, content, notification type, read flag, creation time.
- `ActivityLog`: actor account, action, entity type, entity id, old/new JSON snapshots, IP address, creation time.

No database schema change is required for the first Phase 7 slice. Notification content will be plain Vietnamese text. If the frontend later needs deep links or structured payloads, a future slice can add `metadataJson` or `targetUrl`.

## Notification Behavior

Notifications are in-app records only. A business event creates one notification per recipient account. Users fetch notifications through API and mark them as read.

Planned events:

| Event | Recipient | Type | Title |
| --- | --- | --- | --- |
| Schedule published | Employees with assignments in the period | `SCHEDULE_PUBLISHED` | `Lịch làm việc mới đã được công bố` |
| Leave approved | Requesting employee | `LEAVE_APPROVED` | `Đơn nghỉ của bạn đã được duyệt` |
| Leave rejected | Requesting employee | `LEAVE_REJECTED` | `Đơn nghỉ của bạn đã bị từ chối` |
| Swap requested | Target employee | `SWAP_REQUESTED` | `Bạn có yêu cầu đổi ca mới` |
| Target accepts swap | Requesting employee | `SWAP_TARGET_ACCEPTED` | `Yêu cầu đổi ca đang chờ quản lý duyệt` |
| Target rejects swap | Requesting employee | `SWAP_TARGET_REJECTED` | `Yêu cầu đổi ca đã bị từ chối` |
| Manager approves swap | Requesting employee and target employee | `SWAP_APPROVED` | `Yêu cầu đổi ca đã được duyệt` |
| Manager rejects swap | Requesting employee and target employee | `SWAP_REJECTED` | `Yêu cầu đổi ca đã bị từ chối` |

Example content:

```text
Lịch làm việc kỳ "Tuần 38" đã được công bố. Vui lòng kiểm tra lịch cá nhân.
```

```text
Đơn nghỉ từ 2026-09-20 đến 2026-09-21 đã được duyệt.
```

## Activity Log Behavior

Activity logs are manager-facing audit records. Each record stores the actor account, action, affected entity type/id, and compact before/after snapshots where useful.

Planned events:

- Employee create/update/lock/unlock.
- Leave approve/reject.
- AI schedule optimization request/result.
- Manual assignment create/update/delete/lock/unlock.
- Schedule publish.
- Swap approve/reject.

Snapshots must be compact maps, not full JPA entities, to avoid lazy-loading loops and leaking unrelated data. For example, an assignment log can include assignment id, work shift id, employee id, status, source, and locked flag.

## Backend Components

### Repositories

- `NotificationRepository`
  - list notifications for recipient, newest first.
  - filter by read state.
  - find notification by id and recipient id.
  - mark all unread notifications for recipient as read.

- `ActivityLogRepository`
  - list logs newest first.
  - optional filters: action, entity type, account id, date range.

### Services

- `NotificationService`
  - `notify(Account recipient, String type, String title, String content)`.
  - `notifyAll(Collection<Account> recipients, ...)` with duplicate recipient protection.
  - `list(CurrentAccountPrincipal principal, Boolean read)`.
  - `markRead(CurrentAccountPrincipal principal, Long notificationId)`.
  - `markAllRead(CurrentAccountPrincipal principal)`.

- `ActivityLogService`
  - `record(Account actor, String action, String entityType, Long entityId, Object oldValue, Object newValue)`.
  - JSON serialization uses `ObjectMapper` and fails loudly if the snapshot cannot be serialized.
  - listing is manager-only.

### Controllers

- `EmployeeNotificationController`
  - `GET /api/employee/notifications?read=true|false`
  - `PATCH /api/employee/notifications/{notificationId}/read`
  - `PATCH /api/employee/notifications/read-all`

- `AdminActivityLogController`
  - `GET /api/admin/activity-logs?action=&entityType=&accountId=&from=&to=`

The notification API is exposed under `/api/employee` because current frontend navigation has employee notification pages. The service itself should allow any authenticated account to receive/read notifications if future manager inbox pages use the same service.

## Event Wiring

Event creation will be added inside the existing transactional service methods after the business state is updated and before returning the response. The notification/log writes participate in the same transaction as the business change.

Initial wiring points:

- `AdminEmployeeService`: create/update/lock/unlock logs.
- `AdminLeaveRequestService`: approve/reject logs and employee notifications.
- `AdminOptimizationService`: optimize logs for success, infeasible, and failure outcomes.
- `AdminShiftAssignmentService`: create/update/delete/lock/unlock logs.
- `AdminSchedulePeriodService`: publish log and schedule-published notifications.
- `EmployeeSwapRequestService`: swap requested and target response notifications.
- `AdminSwapRequestService`: approve/reject logs and notifications to related employees.

If a recipient has no account, notification creation will skip that recipient. Audit logging still records the action when the actor exists.

## Error Handling

- User cannot read or mark another user's notification; return 404 to avoid leaking ids.
- Invalid notification filters return 400.
- Manager-only activity log API returns 403 through existing security patterns.
- If notification/log persistence fails because of invalid data, the surrounding transaction fails. This keeps audit/notification consistency explicit.

## Testing Strategy

Unit and service-level tests should follow current backend patterns.

Notification tests:

- user lists only own notifications.
- user can filter read/unread notifications.
- user can mark one notification read.
- user can mark all own notifications read.
- user cannot mark another user's notification.

Activity log tests:

- record writes actor/action/entity/snapshots.
- manager list filters by action, entity type, account id, and date range.
- non-manager access is rejected through controller/security tests where practical.

Event wiring tests:

- approve/reject leave creates log and employee notification.
- publish schedule creates log and notifications for assigned employees.
- create/respond/approve/reject swap creates expected notifications/logs.
- manual assignment changes create logs.
- AI optimization creates logs for terminal outcomes.

## Out Of Scope

- Frontend API integration.
- Websocket/realtime notification delivery.
- Email/push notification delivery.
- Notification metadata/deep links.
- Export/reporting.
- Full activity timeline UI.

## Acceptance Criteria

- Backend exposes notification list and mark-read APIs.
- Backend exposes manager activity log list API.
- Phase 7 events create the expected records.
- Existing backend tests remain green.
- New tests cover notification isolation, activity log recording, and core event wiring.

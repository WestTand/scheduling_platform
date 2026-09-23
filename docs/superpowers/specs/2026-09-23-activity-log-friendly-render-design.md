# Activity Log Friendly Render Design

## Context

The manager activity log table currently shows backend-oriented values in backend mode:

- `target`: raw entity names such as `leave_requests #2`.
- `description`: raw JSON snapshots such as `{"status":"APPROVED"}`.

These values are useful for developers but not suitable for end users.

## Design

Keep the backend API unchanged. Convert activity-log rows in the frontend adapter into business-facing Vietnamese text.

Entity labels:

- `leave_requests` -> `Đơn nghỉ phép`
- `swap_requests` -> `Yêu cầu đổi ca`
- `preferences` -> `Nguyện vọng làm việc`
- `schedule_periods` -> `Lịch làm việc`
- `employees` -> `Nhân viên`
- `optimization_runs` -> `Lượt tối ưu lịch`

When `entityId` is present, render `Tên đối tượng #id`; when absent, render only the entity label.

Action labels:

- `APPROVE` -> `Duyệt`
- `REJECT` -> `Từ chối`
- `CREATE` -> `Tạo mới`
- `UPDATE` -> `Cập nhật`
- `LOCK` -> `Khóa`
- `UNLOCK` -> `Mở khóa`
- `PUBLISH` -> `Công bố`
- `OPTIMIZE` -> `Tối ưu lịch`

Descriptions should be short sentences, not JSON. Known action/entity combinations get specific copy, for example:

- `APPROVE + leave_requests`: `Quản lý đã duyệt đơn nghỉ phép.`
- `REJECT + leave_requests`: `Quản lý đã từ chối đơn nghỉ phép.`
- `PUBLISH + schedule_periods`: `Quản lý đã công bố lịch làm việc.`
- `CREATE + employees`: `Quản lý đã tạo hồ sơ nhân viên.`
- `UPDATE + employees`: `Quản lý đã cập nhật hồ sơ nhân viên.`

Fallback description: `Đã thực hiện thao tác [Hành động] trên [Đối tượng].`

## Testing

- Adapter tests cover known entity/action mappings.
- Adapter tests cover unknown entity/action fallback.
- Frontend build verifies table usage remains type-safe.

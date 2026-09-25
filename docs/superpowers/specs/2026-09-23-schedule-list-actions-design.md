# Schedule List Actions Design

## Goal

Replace the placeholder `Mở lịch` action on `/manager/schedules` with real schedule actions so managers can open, edit, approve, publish, and cancel schedules from the list.

## Scope

- Update the schedule list UI only.
- Backend mode calls existing schedule period APIs.
- Mock mode keeps non-destructive placeholder behavior for state-changing actions.
- Do not add new backend endpoints.
- Do not redesign the schedule list layout.

## Actions

Each schedule row exposes these actions:

- `Xem chi tiết`: navigates to `/manager/schedules/{id}`.
- `Chỉnh sửa`: navigates to `/manager/schedules/{id}/edit`.
- `Duyệt`: calls `PATCH /api/admin/schedule-periods/{id}/approve`; available for `DRAFT` schedules.
- `Công bố`: calls `PATCH /api/admin/schedule-periods/{id}/publish`; available for `APPROVED` schedules.
- `Hủy`: calls `PATCH /api/admin/schedule-periods/{id}/cancel`; available for `DRAFT` and `APPROVED` schedules.

## Data Flow

- `SchedulesPage` passes action handlers into `ScheduleList`.
- `ScheduleList` renders buttons based on each schedule status.
- Backend action handlers call `backendScheduleService` methods.
- After a successful backend action, `SchedulesPage` reloads the period list using the current filters.
- Success and error messages render in the existing notice area.

## Service Changes

Add methods to `backendScheduleService`:

- `approvePeriod(id)`
- `publishPeriod(id)`
- `cancelPeriod(id)`

Each returns the adapted `WorkSchedule` response.

## Error Handling

- API errors are shown as a red notice.
- Successful actions are shown as a blue notice.
- If an action is not valid for the current status, the button is not rendered.

## Testing

- Add service tests for approve, publish, and cancel endpoints.
- Add a static page/component test that backend-mode list actions no longer render the `Mở lịch` placeholder and include real action labels.
- Run frontend tests, build, and lint after implementation.

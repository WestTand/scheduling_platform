# Schedule Detail Backend Polish Design

## Goal

Polish `/manager/schedules/:id` so it shows real backend schedule detail data and supports backend status actions consistently with the schedule list.

## Scope

- Update frontend behavior only.
- Use existing backend schedule detail and period status APIs.
- Do not add backend APIs.
- Preserve the existing page layout and visual style.

## Backend Mode Behavior

- Load schedule detail from `/api/admin/schedule-periods/{id}/schedule`.
- Display real counts for employees, assignments, shifts, and warnings.
- Display real assignments grouped by the page's existing presentation style.
- Show backend-mode status actions:
  - `Chỉnh sửa` for navigable edit access.
  - `Duyệt` when status is `DRAFT`.
  - `Công bố` when status is `APPROVED`.
  - `Hủy` when status is `DRAFT` or `APPROVED`.
- Reload detail after approve, publish, or cancel.
- For `PUBLISHED` and `CANCELLED`, keep the page read-only and avoid suggesting mutable actions beyond navigation.

## Mock Mode Behavior

- Preserve existing mock detail behavior.
- Mock-only labels may remain in mock mode.

## Error Handling

- Show loading state while backend detail is loading.
- Show red error notice when loading or status actions fail.
- Show blue success notice after successful status actions.

## Testing

- Add/adjust frontend tests for detail-page action visibility rules.
- Run focused frontend tests, build, and lint.

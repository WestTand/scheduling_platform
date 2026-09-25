# Schedule Edit Backend Polish Design

## Goal

Polish `/manager/schedules/:id/edit` so backend mode no longer looks like a mock screen and prevents invalid edits for immutable schedules.

## Scope

- Update frontend behavior only.
- Use existing backend APIs for schedule detail, work shifts, employees, and assignment mutations.
- Do not add backend APIs.
- Do not redesign the page layout.

## Backend Mode Behavior

- Replace mock-only language with backend-mode language.
- Hide backend-unsupported status buttons (`Lưu bản nháp`, `Gửi duyệt`) in backend mode.
- Disable assignment mutations when schedule status is `PUBLISHED` or `CANCELLED`.
- Show a banner when editing is locked because the schedule is published or cancelled.
- Keep create/update/delete assignment actions enabled for editable statuses.

## Mock Mode Behavior

- Preserve existing mock interaction behavior.
- Mock-only labels and buttons may remain in mock mode.

## Notices

- Success messages render as blue notices.
- Error messages render as red notices.
- Backend action errors must not be shown as success notices.

## Data Refresh

- After backend create/update/delete assignment, reload the schedule detail.
- Update both `detail` and `assignments` from the refreshed backend data so counts and status cards stay consistent.

## Testing

- Add/adjust frontend tests to cover backend-mode locked schedules and visible backend-mode text.
- Run focused frontend tests, build, and lint.

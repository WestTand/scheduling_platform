# Schedule Generation Backend Polish Design

## Goal

Polish `/manager/schedule-generation` so managers can create a backend schedule period, run AI optimize for it, and continue to the generated schedule detail.

## Scope

- Update frontend behavior only.
- Use existing backend APIs for creating schedule periods and optimizing schedules.
- Do not add backend APIs.
- Preserve the existing page structure and visual style.

## Backend Mode Behavior

- The create form submits `periodName`, `startDate`, `endDate`, and `versionNo` to the backend.
- After a period is created, the page stores the created period and shows a link/action to its detail page.
- `AI Optimize` is enabled only when a backend period exists.
- Running `AI Optimize` calls the backend optimize endpoint for the created period.
- Optimize success displays `aiStatus`, `solverStatus`, and `assignmentCount` when available.
- Create and optimize success messages render as blue notices.
- Create and optimize errors render as red notices.

## Mock Mode Behavior

- Preserve existing mock/demo behavior.
- Mock-only labels may remain in mock mode.

## Navigation

- After successful create or optimize, the manager can navigate to `/manager/schedules/{id}`.
- The page does not automatically navigate away immediately, so the manager can read optimize output first.

## Testing

- Add helper tests for optimize enablement and notice styling.
- Run focused frontend tests, build, and lint.

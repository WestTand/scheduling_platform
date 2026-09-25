# Scheduling Pages Backend Integration Design

## Context

The manager scheduling pages are still mostly mock-driven even though backend APIs exist for schedule periods, work shifts, assignments, publishing, and AI optimization. With dev seed data available, these pages should use backend mode so managers can test real flows.

## Scope

Integrate backend mode for these manager pages:

- `/manager/schedules`
- `/manager/schedules/:id`
- `/manager/schedules/:id/edit`
- `/manager/schedule-generation`

## Behavior

Schedule list:

- Load periods from `GET /api/admin/schedule-periods`.
- Support status/date/search filters on backend data.
- Remove mock-only messaging in backend mode.

Schedule detail:

- Load period detail from `GET /api/admin/schedule-periods/{periodId}`.
- Load schedule assignments from `GET /api/admin/schedule-periods/{periodId}/schedule`.
- Render real work shifts and assigned employees.
- Keep warnings empty for this slice.

Schedule edit:

- Load work shifts from `GET /api/admin/schedule-periods/{periodId}/work-shifts`.
- Load employees from `GET /api/admin/employees`.
- Support create/update/delete assignment and lock/unlock assignment through existing admin endpoints.
- Respect backend draft-only rules and surface API errors.

Schedule generation:

- Select an existing period and call `POST /api/admin/schedule-periods/{periodId}/optimize`.
- Display optimize result or backend/AI error.

## Non-Goals

- No large UI redesign.
- No new backend scheduling API.
- No full warning/rule visualization.
- No employee schedule page integration in this slice.
- No new seed data.

## Testing

- Add frontend adapter/service tests for schedule periods, schedule detail, assignments, and optimization calls.
- Run frontend focused tests, full frontend tests, and build.
- Runtime smoke with seeded manager account against backend mode.

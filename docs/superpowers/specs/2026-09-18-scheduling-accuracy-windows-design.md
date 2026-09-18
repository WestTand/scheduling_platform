# Scheduling Accuracy Windows Design

## Goal

Persist exact work-shift time windows so backend schedule data can represent overnight shifts correctly and provide stable inputs for later AI and publish-validation slices.

This slice covers backend persistence, derivation, admin responses, tests, and documentation. It does not change AI-service solver behavior, frontend screens, manual time overrides, CP-SAT result validation, or migration tooling.

## Decisions

- `ShiftType.startTime`, `ShiftType.endTime`, and `ShiftType.durationHours` remain the source of truth for a work shift's time window.
- `CreateWorkShiftRequest` does not accept `startsAt`, `endsAt`, `workMinutes`, or `isTransitionShift`. Backend derives them.
- Persist `startsAt` and `endsAt` as Java `OffsetDateTime` values with the Vietnam offset from `Asia/Ho_Chi_Minh`.
- Use the Vietnam scheduling zone `Asia/Ho_Chi_Minh` when converting `workDate + LocalTime` into persisted date-times.
- A transition shift means the derived `endsAt` local date is after the derived `startsAt` local date.
- Existing rows may have null persisted window fields after `ddl-auto=update`; responses should compute fallback window values from `workDate + shiftType` instead of failing.
- No Flyway/Liquibase, DB backfill job, frontend update, or AI schema change in this slice.

## Current Gaps

- `work_shifts` stores only `workDate`, not exact start/end date-times.
- Overnight shifts such as `17:00-00:30` require downstream code to infer the end date.
- Schedule responses expose `ShiftType` local start/end times but not concrete datetimes for a specific `workDate`.
- AI contract documents `startsAt`/`endsAt`, but backend does not yet persist these values on `WorkShift`.

## In Scope

### WorkShift Fields

Extend `WorkShift` with nullable persisted fields:

- `startsAt`: exact shift start datetime, Java type `OffsetDateTime`.
- `endsAt`: exact shift end datetime, Java type `OffsetDateTime`.
- `workMinutes`: integer number of planned working minutes.
- `isTransitionShift`: boolean, true when the shift crosses into the next local date.

Column names:

- `starts_at`
- `ends_at`
- `work_minutes`
- `is_transition_shift`

Fields should be nullable at the database level in this slice to avoid breaking existing local/dev databases when Hibernate `ddl-auto=update` adds columns to `work_shifts`.

### Window Derivation

Add a small backend helper/service that derives a window from `LocalDate workDate` and `ShiftType shiftType`:

1. `startsAt = workDate at shiftType.startTime` in `Asia/Ho_Chi_Minh`.
2. `endsAt = workDate at shiftType.endTime` in `Asia/Ho_Chi_Minh`.
3. If `endsAt <= startsAt`, add one day to `endsAt`.
4. `workMinutes = durationHours * 60`, rounded to the nearest integer only after converting from the stored decimal hours.
5. `isTransitionShift = endsAt.toLocalDate() is after startsAt.toLocalDate()`.

Examples:

| Shift window | Work date | Derived startsAt | Derived endsAt | workMinutes | transition |
|---|---|---|---|---:|---|
| `08:00-16:00`, `8.0h` | `2026-09-14` | `2026-09-14T08:00+07:00` | `2026-09-14T16:00+07:00` | 480 | false |
| `17:00-00:30`, `7.5h` | `2026-09-14` | `2026-09-14T17:00+07:00` | `2026-09-15T00:30+07:00` | 450 | true |
| `16:00-00:00`, `8.0h` | `2026-09-14` | `2026-09-14T16:00+07:00` | `2026-09-15T00:00+07:00` | 480 | true |
| `00:30-08:00`, `7.5h` | `2026-09-14` | `2026-09-14T00:30+07:00` | `2026-09-14T08:00+07:00` | 450 | false |

### Work Shift Creation

When creating a `WorkShift`, backend keeps the current validation order:

- manager access;
- period exists and is `DRAFT`;
- active shift type exists;
- `workDate` is inside the period;
- shift type is applicable on the date;
- `requiredStaff` is inside `[shiftType.minStaff, shiftType.maxStaff]`;
- note length is valid.

After validation, backend derives the window and persists the four new fields on the `WorkShift` entity before saving.

### Responses

Update all backend schedule/work-shift responses that currently expose shift local start/end times to also include exact persisted windows:

- `AdminWorkShiftResponse`
- `AdminPeriodScheduleResponse.ScheduledShift`
- `EmployeeScheduleItemResponse`

Each response should include:

- `startsAt`
- `endsAt`
- `workMinutes`
- `isTransitionShift`

Response mapping rules:

- If all four persisted window fields are non-null, use persisted values.
- If any persisted window field is null and `workDate + shiftType` are available, compute the full window from `workDate + shiftType`; do not mix persisted and computed fields in the same response.
- Keep existing `shiftStartTime` and `shiftEndTime` fields for compatibility with current frontend/mock expectations.

This fallback exists only for old DB rows. New creates must persist all four fields.

### Documentation Updates

Update:

- `docs/database-design-v2.md` with new `work_shifts` columns and nullable/backfill note.
- `docs/backend-ai-scheduling-contract.md` to note backend now persists exact work-shift windows for later AI payload work. Do not change the AI request/response schema or schema version in this slice.

## Out Of Scope

- Manual override fields in `CreateWorkShiftRequest`.
- Frontend UI changes.
- AI-service solver/scoring changes.
- CP-SAT result validation before save/publish.
- Holiday completeness or publish blocking.
- Flyway/Liquibase migration.
- Backfill job for existing `work_shifts` rows.
- Changing `ShiftType` definitions or shift-code seeding.

## Testing

Add/extend backend tests:

- Window helper tests for same-day shift.
- Window helper tests for overnight end `00:30`.
- Window helper tests for end exactly `00:00`.
- Window helper tests for early-morning `00:30-08:00` not being transition.
- Admin work-shift service test that create persists `startsAt`, `endsAt`, `workMinutes`, and `isTransitionShift`.
- Admin work-shift response tests for persisted values and fallback values when persisted fields are null.
- Manager period schedule response tests for the exact window fields.
- Employee schedule response tests for the exact window fields.
- Existing work-shift controller/service tests updated for the new response fields where JSON shape is asserted.
- Full backend suite must pass.

## Follow-Ups

- Send persisted windows in the backend AI payload if current snapshot code still derives them elsewhere.
- Add AI-service validation for datetime windows and overnight rest constraints.
- Add CP-SAT result validation before saving or publishing generated assignments.
- Add Flyway/Liquibase migration and optional backfill if production-style migration control becomes required.

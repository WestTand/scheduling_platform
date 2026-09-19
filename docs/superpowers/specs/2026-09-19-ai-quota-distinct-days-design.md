# AI Quota Distinct Days Design

## Goal

Make AI quota enforcement match the project requirement that overtime and bathing quotas are counted by distinct work dates per employee per ISO week, not by number of work-shift rows.

This slice updates AI-service quota semantics, tests, and documentation only. It does not add backend AI integration, change backend scheduling-rule API field names, alter `schemaVersion`, consume `penaltyWeightMultiplier`, change frontend, or add result persistence.

## Decisions

- Keep the existing AI contract field names `overtimeShiftsPerWeek` and `bathingShiftsPerWeek` for compatibility.
- Keep `schemaVersion` as `1.0`.
- Treat the two existing quota values as max distinct `workDate` count per employee per ISO week:
  - `OVERTIME`: max distinct overtime work dates per employee per ISO week.
  - `BATHING`: max distinct bathing/thong-tam work dates per employee per ISO week.
- If an employee is assigned multiple work shifts in the same quota category on the same `workDate`, that date counts once.
- If an employee is assigned quota-category shifts on two different dates in the same ISO week, that counts as two.
- Boundary assignments count toward the same distinct-date quota by `boundary.startsAt.date()` and its `shiftType.quotaCategory`.
- Boundary quota dates intentionally use `startsAt.date()` in this slice to match the existing solver's boundary grouping for daily and weekly limits.
- `ON_CALL` keeps the existing `onCallEveryNDays` gap rule. It is not converted into weekly distinct-day counting in this slice.
- Backend scheduling-rule DTO/API field names remain unchanged in this slice. Renaming `*ShiftsPerWeek` to `*DaysPerWeek` would be a later breaking contract cleanup.
- Backend-facing rule descriptions and docs should clarify day-count semantics so managers do not read the legacy field names as shift-row counts.

## Current Gaps

- `docs/Tai_Lieu_Phan_Tich_Thiet_Ke_He_Thong_Phan_Cong_Lich.md` states quotas as `Ngoai gio: 1 ngay/tuan` and `Thong tam: 2 ngay/tuan`.
- `docs/project_plan.md` also describes Phase 6 quota tasks as `ngoai gio 1 ngay/tuan` and `thong tam 2 ngay/tuan`.
- `docs/database-design-v2.md` examples use `Ngoai gio: 1 ngay/tuan` and `Thong tam: 2 ngay/tuan`.
- The current AI contract explicitly notes a gap: solver counts `OVERTIME` and `BATHING` by work-shift rows per ISO week.
- `ai-service/app/solver/scheduler.py` currently sums assignment variables for category work shifts, so two same-category shifts on one date count twice.

## In Scope

### AI Solver Quota Semantics

Update the quota block in `ai-service/app/solver/scheduler.py` so `OVERTIME` and `BATHING` quota limits count distinct dates.

For each `category`, `employee_id`, and ISO `week`:

1. Group in-period work shifts by `workDate` where `shiftType.quotaCategory == category`.
2. For each grouped date, create a Boolean variable that is true when the employee is assigned at least one matching shift on that date.
3. Add constraints so the date Boolean is equivalent to the OR of that employee's assignment variables for the category/date group.
4. Add boundary assignment dates for the same employee/category/week.
5. Enforce `sum(dateBooleans) + boundaryDistinctDateCount <= limit`.

Boundary handling must deduplicate boundary dates for the same employee/category/week. If two boundary assignments in the same category occur on the same date, they count once.

### Contract Documentation

Update `docs/backend-ai-scheduling-contract.md` to clarify:

- `overtimeShiftsPerWeek` is a legacy field name; with `schemaVersion = 1.0`, it means max distinct `OVERTIME` work dates per employee per ISO week.
- `bathingShiftsPerWeek` is a legacy field name; with `schemaVersion = 1.0`, it means max distinct `BATHING` work dates per employee per ISO week.
- Multiple same-category shifts on the same `workDate` count once.
- Boundary assignments count by `startsAt.date()`.
- Replace the previous follow-up note about deciding between work-shift count and distinct-day count with a statement that this slice resolves the ambiguity.

Do not rename contract fields or change examples beyond explanatory text in this slice.

### Project Plan Documentation

Update `docs/project_plan.md` only if the implementation completes, marking the quota semantics tasks or adding a concise note that `OVERTIME` and `BATHING` are counted by distinct day. Do not mark unrelated Phase 6 backend integration tasks complete.

### Backend Rule Description Documentation

Update docs that describe scheduling-rule fields without changing Java DTO/API names:

- `OVERTIME_SHIFTS_PER_WEEK` keeps mapping to `quotas.overtimeShiftsPerWeek`, but its description becomes max distinct overtime work dates per employee per ISO week.
- `BATHING_SHIFTS_PER_WEEK` keeps mapping to `quotas.bathingShiftsPerWeek`, but its description becomes max distinct bathing/thong-tam work dates per employee per ISO week.
- Backend API JSON field names remain unchanged.

## Out Of Scope

- Backend AI snapshot builder or HTTP call to AI service.
- Persisting AI results to `ShiftAssignment` or `OptimizationRun`.
- `penaltyWeightMultiplier` consumption in AI scoring.
- Renaming scheduling-rule API fields.
- Java backend scheduling-rule implementation changes, unless needed for documentation strings only.
- `schemaVersion` changes.
- Frontend changes.
- Backfill or migration work.
- Changing `ON_CALL` behavior beyond ensuring existing tests still pass.

## Testing

Add or update AI-service pytest coverage:

- Same employee can satisfy `overtimeShiftsPerWeek = 1` with two `OVERTIME` shifts on the same `workDate`; both shifts can be assigned if staffing requires it and no other hard constraints block it.
- Same employee cannot be assigned `OVERTIME` shifts on two different work dates in the same ISO week when `overtimeShiftsPerWeek = 1`; the solver assigns another employee when one is available and returns infeasible when no valid alternative exists.
- `BATHING` with limit `2` allows assignments on two distinct dates but not three distinct dates for the same employee in the same ISO week.
- Boundary assignment in `OVERTIME` or `BATHING` consumes one distinct date in the same ISO week.
- Existing tests for rest, leave, locked assignments, on-call gap, naive datetime rejection, and normal success still pass.

Quota test fixtures must avoid unrelated blockers such as overlap, minimum rest, and max daily work hours. Use shorter durations, higher daily limits, or non-overlapping windows when necessary so each test isolates quota semantics.

Run the AI-service pytest suite from `ai-service/` with `.\.venv\Scripts\pytest.exe`. Run `git diff --check`. Do not require backend Maven tests for this AI-service-only implementation unless backend Java files are changed.

## Follow-Ups

- Consider a future `schemaVersion = 1.1` that renames `overtimeShiftsPerWeek` and `bathingShiftsPerWeek` to day-based names.
- Add richer infeasible diagnostics that identify quota-category/date conflicts.
- Add backend AI snapshot builder and mocked AI-service integration tests in a separate Phase 6 slice.
- Consume `penaltyWeightMultiplier` in AI scoring in a separate slice.

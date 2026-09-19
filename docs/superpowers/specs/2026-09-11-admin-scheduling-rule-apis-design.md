# Admin Scheduling Rule APIs Design

## Context

Phase 3 Admin Core has backend APIs for employee/account administration and shift type configuration. The remaining Phase 3 backend slice is scheduling rule management. These rules are the contract between business configuration and later manual/AI scheduling.

`SchedulingRule` already exists in the database model with `ruleCode`, `ruleName`, `ruleType`, `ruleValueJson`, `weight`, and `active`. This feature exposes a small global configuration API that stores rule rows in that existing shape while returning a stable DTO that the backend and AI-service can use later.

## Scope

Implement backend APIs under `/api/admin/scheduling-rules`:

- `GET /api/admin/scheduling-rules`
- `PUT /api/admin/scheduling-rules`

All endpoints are available to `MANAGER` and `ADMIN`. `EMPLOYEE` must receive `403`.

This slice does not include department-specific rules, schedule-period snapshots, invoking the AI service, creating work shifts, or frontend screens.

## Project Requirements Alignment

This design follows the project description and scheduling use case:

- The system uses Google OR-Tools CP-SAT as the optimization engine.
- Scheduling rules must be configured before automatic schedule generation.
- Hard constraints must not be violated by the solver.
- Soft constraints become weighted objective penalties.
- Required quotas include 1 overtime shift per week, 2 bathing shifts per week, and 1 on-call shift every 6 days when applicable.
- Required hard rules include no overlapping shifts, at least 12 hours of rest between shifts, at most 12 work hours per day, no assignments during approved leave, preserving locked assignments, active employees only, and active shift types only.
- This API stores only the configurable rule values and soft weights. Constraints that depend on real schedule data, such as overlap checks, approved leave, locked assignments, active employees, and active shift types, are enforced later by manual scheduling and AI scheduling flows using those data sources.
- LLM usage is optional support for explanations and suggestions. It must not replace OR-Tools for final assignment decisions.

## Configuration Model

Use one global rule set for MVP. The API response groups database rows into these sections:

- `hardRules`
- `quotas`
- `softWeights`

The backend persists each rule as one `SchedulingRule` row with a stable `ruleCode`:

| Rule code | Type | API field | Purpose |
|---|---|---|---|
| `MIN_REST_HOURS_BETWEEN_SHIFTS` | `HARD` | `hardRules.minRestHoursBetweenShifts` | Minimum rest time between two assigned shifts. |
| `MAX_WORK_HOURS_PER_DAY` | `HARD` | `hardRules.maxWorkHoursPerDay` | Maximum total scheduled hours per employee per day. |
| `MAX_SHIFTS_PER_WEEK` | `HARD` | `hardRules.maxShiftsPerWeek` | Maximum number of shifts per employee per week. |
| `OVERTIME_SHIFTS_PER_WEEK` | `HARD` | `quotas.overtimeShiftsPerWeek` | Maximum distinct overtime work dates per employee per ISO week (legacy field name kept). |
| `BATHING_SHIFTS_PER_WEEK` | `HARD` | `quotas.bathingShiftsPerWeek` | Maximum distinct bathing work dates per employee per ISO week (legacy field name kept). |
| `ON_CALL_EVERY_N_DAYS` | `HARD` | `quotas.onCallEveryNDays` | Minimum cycle for on-call shifts when applicable. |
| `FAIRNESS_WEIGHT` | `SOFT` | `softWeights.fairness` | Penalty weight for uneven shift distribution. |
| `PREFERENCE_WEIGHT` | `SOFT` | `softWeights.preference` | Penalty weight for violating employee preferences. |
| `WEEKEND_WEIGHT` | `SOFT` | `softWeights.weekend` | Penalty weight for uneven weekend assignments. |
| `HEAVY_SHIFT_WEIGHT` | `SOFT` | `softWeights.heavyShift` | Penalty weight for consecutive or concentrated heavy shifts. |
| `WORK_HOUR_BALANCE_WEIGHT` | `SOFT` | `softWeights.workHourBalance` | Penalty weight for uneven total work hours. |

Each hard/quota rule stores its numeric value in `ruleValueJson`, for example `{ "value": 12 }`. Each soft rule stores `{ "enabled": true }` in `ruleValueJson` and its numeric coefficient in `weight`.

## API Behavior

### Get Scheduling Rules

`GET /api/admin/scheduling-rules` returns the global configuration.

If no rows exist yet, the service creates and returns default rule rows. Defaults are:

- `minRestHoursBetweenShifts`: `12`
- `maxWorkHoursPerDay`: `12`
- `maxShiftsPerWeek`: `6`
- `overtimeShiftsPerWeek`: `1`
- `bathingShiftsPerWeek`: `2`
- `onCallEveryNDays`: `6`
- `fairness`: `10.00`
- `preference`: `5.00`
- `weekend`: `5.00`
- `heavyShift`: `8.00`
- `workHourBalance`: `10.00`

Response fields:

- `hardRules`
- `quotas`
- `softWeights`

### Update Scheduling Rules

`PUT /api/admin/scheduling-rules` replaces the global configuration values. It upserts the stable rule rows and returns the updated grouped response.

Request fields:

- `hardRules.minRestHoursBetweenShifts`
- `hardRules.maxWorkHoursPerDay`
- `hardRules.maxShiftsPerWeek`
- `quotas.overtimeShiftsPerWeek`
- `quotas.bathingShiftsPerWeek`
- `quotas.onCallEveryNDays`
- `softWeights.fairness`
- `softWeights.preference`
- `softWeights.weekend`
- `softWeights.heavyShift`
- `softWeights.workHourBalance`

Partial updates are not supported in this slice. Clients send the complete config returned by `GET`, modify desired values, then submit the full object with `PUT`.

## Validation Rules

Use Bean Validation for simple numeric constraints:

- `minRestHoursBetweenShifts`: required, `0` to `24`.
- `maxWorkHoursPerDay`: required, `1` to `24`.
- `maxShiftsPerWeek`: required, `0` to `14`.
- quota values: required, `0` to `14` except `onCallEveryNDays`, which is `1` to `31`.
- soft weights: required, `0.00` to `999.99`.

Use service validation for cross-field rules:

- `minRestHoursBetweenShifts + maxWorkHoursPerDay <= 24`.

Invalid cross-field validation returns `400 BAD_REQUEST` with message `Invalid scheduling rules request`.

## Data Flow

Get flow:

1. Controller checks role and delegates to service.
2. Service loads all known rule codes.
3. Service creates any missing default rows.
4. Service groups rule rows into the response DTO.

Update flow:

1. Controller validates request body.
2. Service validates cross-field constraints.
3. Service loads existing rows by rule code.
4. Service updates or creates each known row.
5. Repository saves rows in one transaction.
6. Service returns grouped response DTO.

Future AI scheduling flow:

1. Backend builds the AI scheduling payload for a period.
2. Backend includes this grouped rules config with employees, active shift types, work shifts, approved leave requests, preferences, and locked assignments.
3. OR-Tools maps hard/quota values to required CP-SAT constraints.
4. OR-Tools maps soft weights to objective penalty coefficients.
5. LLM features may use the same config to explain infeasible schedules or suggest tuning, but assignment decisions remain with OR-Tools.

## Components

Create a module parallel to existing admin modules:

- `SchedulingRuleRepository`: Spring Data repository with lookup by `ruleCode` and list by known codes.
- `AdminSchedulingRuleService`: default initialization, read, update, validation, mapping, and JSON value parsing/formatting.
- `AdminSchedulingRuleController`: HTTP endpoints and role protection.
- DTOs under `scheduling.scheduling.admin.dto`:
  - `AdminSchedulingRulesResponse`
  - `SchedulingHardRulesRequest`
  - `SchedulingQuotaRulesRequest`
  - `SchedulingSoftWeightsRequest`
  - `UpdateSchedulingRulesRequest`

## Error Handling

Malformed JSON in existing `ruleValueJson` is a data integrity problem. The service should fail fast with `IllegalStateException`; this is not user input and should not be silently replaced.

Request validation errors return the existing validation error response behavior. Cross-field validation returns `400 BAD_REQUEST`.

## Testing

Add service and controller tests covering:

- Get creates missing default rules and returns grouped response.
- Get reuses existing rows and does not duplicate defaults.
- Update persists all hard, quota, and soft values.
- Update rejects invalid cross-field configuration.
- Update rejects invalid request body through Bean Validation.
- Controller allows `MANAGER` / `ADMIN` and rejects `EMPLOYEE`.
- Full backend context smoke test includes any new repository mock required by the existing JPA-excluded test setup.

Run full backend verification with `mvnw.cmd clean test` on Windows.

## Notes

This design intentionally starts with global rules. Department-specific configs and schedule-period snapshots are useful later, but they depend on stronger department/period workflows and would slow down the MVP without improving the AI-service contract.

# Shift Rule Foundation Design

## Goal

Make the backend data model and work-shift validation match `docs/quy_dinh_ca_lam_viec.md` for the fixed shift catalog, fixed-date public holidays, day applicability, and future night-shift weighting.

This slice is backend/database foundation only. It does not change the AI solver objective, backend-to-AI scheduling payload, frontend screens, Excel/PDF export, or manual assignment APIs.

## Current Gaps

- `shift_types` can store start/end time and duration, but does not store which weekdays a shift type is allowed on.
- There is no holiday model. The approved scope uses a built-in fixed-date holiday calendar instead of manager-entered holiday rows.
- `duration_hours` is not capped at 8 hours by service validation.
- There is no field for future per-shift penalty weighting. Night-shift multiplier is stored now but remains `1.00` until an AI-service slice uses it.
- The six standard shift types are not seeded automatically.

## Scope

Add backend support for:

- Fixed-date holiday detection for `01/01`, `30/04`, `01/05`, and `02/09`.
- Shift type applicability fields:
  - `applicableWeekdays`: comma-separated ISO day codes such as `MON,TUE,WED,THU,FRI`.
  - `appliesOnHolidays`: boolean.
  - `penaltyWeightMultiplier`: `BigDecimal`, default `1.00`.
- Idempotent startup seed for the six standard shift types.
- Validation that shift types cannot exceed 8 work hours per shift.
- Validation that work shifts are only created for dates allowed by the shift type.

Out of scope:

- Lunar holidays, Tet, Hung Kings' Commemoration Day, substitute holidays, or year-specific holiday files.
- Holiday CRUD/API or a `holidays` database table.
- Renaming `BATHING` to a better category name. `BATHING` remains the temporary category for `Thong tam` to avoid changing the AI contract in this slice.
- AI-service scoring changes using `penaltyWeightMultiplier`.
- Database migration framework changes. The project currently uses `spring.jpa.hibernate.ddl-auto=update`.

## Data Model

Extend `ShiftType` with:

```java
@Column(name = "applicable_weekdays", nullable = false, length = 40)
@Builder.Default
private String applicableWeekdays = "MON,TUE,WED,THU,FRI,SAT,SUN";

@Column(name = "applies_on_holidays", nullable = false)
@Builder.Default
private Boolean appliesOnHolidays = false;

@Column(name = "penalty_weight_multiplier", nullable = false, precision = 4, scale = 2)
@Builder.Default
private BigDecimal penaltyWeightMultiplier = BigDecimal.ONE;
```

`applicableWeekdays` uses uppercase ISO day abbreviations only:

- `MON`
- `TUE`
- `WED`
- `THU`
- `FRI`
- `SAT`
- `SUN`

Validation rules:

- At least one weekday is required unless `appliesOnHolidays=true`.
- Unknown weekday tokens are rejected with `400 BAD_REQUEST`.
- Duplicate tokens are normalized away.
- Stored value is normalized in ISO order: `MON,TUE,WED,THU,FRI,SAT,SUN`.
- `penaltyWeightMultiplier` must be between `0.00` and `9.99` inclusive.
- `durationHours` must be `> 0` and `<= 8.0`.
- `maxStaff >= minStaff` remains unchanged.

## Fixed Holiday Calendar

Create a small backend helper/service, for example `FixedHolidayCalendar`, with:

```java
boolean isHoliday(LocalDate date)
```

It returns true when month/day is one of:

- January 1
- April 30
- May 1
- September 2

It ignores year-specific substitutions and lunar holidays. This is intentional for this slice.

## Standard Shift Types

Add an idempotent startup seeder that creates or updates shift types by `shiftName`.

Canonical rows:

| Shift name | Start | End | Duration | Overnight | Heavy | Quota category | Applicable weekdays | Applies holidays | Penalty multiplier |
|---|---:|---:|---:|---|---|---|---|---|---:|
| Thong tam | 08:00 | 17:00 | 8.0 | false | true | BATHING | MON,TUE,WED,THU,FRI | false | 1.00 |
| Ngoai gio 1 | 17:00 | 00:30 | 7.5 | true | false | OVERTIME | MON,TUE,WED,THU,FRI | false | 1.00 |
| Ngoai gio 2 | 00:30 | 08:00 | 7.5 | false | true | OVERTIME | MON,TUE,WED,THU,FRI | false | 1.00 |
| Truc sang | 08:00 | 16:00 | 8.0 | false | false | ON_CALL | SAT,SUN | true | 1.00 |
| Truc chieu | 16:00 | 00:00 | 8.0 | true | false | ON_CALL | SAT,SUN | true | 1.00 |
| Truc dem | 00:00 | 08:00 | 8.0 | false | true | ON_CALL | SAT,SUN | true | 1.00 |

Notes:

- ASCII names are used in seed data to stay consistent with existing ASCII-oriented code and test fixtures. Display localization can be handled later.
- `Thong tam` is marked `heavy=true` because existing project docs mention avoiding consecutive heavy shifts such as `Truc` and `Thong tam`.
- `Ngoai gio 2` and `Truc dem` are also marked `heavy=true` to preserve a backend signal for night-like burden until AI scoring uses `penaltyWeightMultiplier`.
- `penaltyWeightMultiplier` remains `1.00` for all six rows in this slice.
- If a row with the same `shiftName` already exists, the seeder updates the canonical fields above so repeated startup is idempotent.

## Work Shift Date Validation

When creating a `WorkShift`, backend validates `workDate` against the selected shift type. Any future work-shift update path must reuse the same validation, but adding a work-shift update API is out of scope for this slice.

Algorithm:

1. Load the target `ShiftType`.
2. Compute `holiday = fixedHolidayCalendar.isHoliday(workDate)`.
3. If `holiday` and `shiftType.appliesOnHolidays == true`, the date is allowed.
4. Otherwise, compute the ISO weekday token for `workDate`.
5. The date is allowed only if the token is present in normalized `shiftType.applicableWeekdays`.
6. If not allowed, reject with `400 BAD_REQUEST` and a stable message such as `Shift type is not applicable on this date`.

Examples:

- `Thong tam` on Monday is allowed.
- `Thong tam` on Saturday is rejected.
- `Ngoai gio 1` on Sunday is rejected.
- `Truc sang` on Saturday is allowed.
- `Truc sang` on Tuesday is rejected unless Tuesday is one of the fixed-date holidays.
- `Truc sang` on `2026-09-02` is allowed because September 2 is a fixed-date holiday.

## API Changes

Update shift type request/response DTOs to include:

- `applicableWeekdays`
- `appliesOnHolidays`
- `penaltyWeightMultiplier`

Existing create/update behavior:

- If `applicableWeekdays` is omitted or `null` on create, default to `MON,TUE,WED,THU,FRI,SAT,SUN`.
- If `appliesOnHolidays` is omitted on create, default to `false`.
- If `penaltyWeightMultiplier` is omitted on create, default to `1.00`.
- Update requests keep the existing full-replacement style: all three new fields are required on update, alongside the existing required fields.
- Holiday-only shift types are supported by sending `applicableWeekdays` as blank and `appliesOnHolidays=true`. The standard seeded shift types do not use holiday-only applicability.

No holiday API is added.

## Error Handling

Use existing error style:

- Invalid shift type request: `400 BAD_REQUEST`, reason `Invalid shift type request`.
- Work shift date not allowed by shift type: `400 BAD_REQUEST`, reason `Shift type is not applicable on this date`.
- Existing not-found and manager authorization behavior remains unchanged.

## Testing

Add/extend backend tests:

- `AdminShiftTypeServiceTest`
  - accepts and normalizes valid weekday lists;
  - rejects unknown weekday tokens;
  - rejects empty applicability when `appliesOnHolidays=false`;
  - rejects `durationHours > 8.0`;
  - defaults multiplier to `1.00` on create;
  - rejects invalid multiplier values.
- `AdminShiftTypeControllerTest`
  - request/response includes new fields;
  - invalid body/service errors still map correctly.
- `AdminWorkShiftServiceTest`
  - allows weekday shift on allowed weekday;
  - rejects weekday shift on weekend;
  - allows on-call shift on weekend;
  - allows on-call shift on fixed-date holiday;
  - rejects on-call shift on normal weekday.
- Seeder tests
  - creates six standard rows when missing;
  - updates existing rows by name;
  - is idempotent on repeated runs.
- Existing full backend suite must pass.

## Documentation Updates

Update:

- `docs/database-design-v2.md` shift type section with the three new columns.
- `docs/backend-ai-scheduling-contract.md` to note `penaltyWeightMultiplier` exists in backend but is not sent to AI-service and is not used by AI in this slice.
- `docs/project_plan.md` only if this slice closes any listed backend/DB planning item. It should not mark AI-service constraints complete.

## Open Follow-Ups

- Rename `BATHING` to a clearer category such as `DAYTIME` or `REGULAR_DAY` in a later backend + AI contract slice.
- Add lunar/year-specific holidays if required.
- Send `penaltyWeightMultiplier` to AI-service and update the solver objective.
- Frontend management UI for new shift type fields.

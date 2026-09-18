# Shift Rule Foundation Design

## Goal

Make backend/database scheduling data ready for the new shift-rule document while keeping the implementation small enough to review safely.

This slice covers canonical shift codes, standard shift seeding, DB-backed holidays seeded from JSON, shift type date applicability, and work-shift creation validation. It does not change AI-service scoring, CP-SAT result validation, published schedule review workflows, frontend screens, or migration tooling.

## Decisions

- Use stable `shiftCode` as the canonical key for shift types. Do not key business logic on display names.
- Keep existing `quotaCategory` values for now (`REGULAR`, `OVERTIME`, `BATHING`, `ON_CALL`) to avoid breaking the current backend/AI contract. `BATHING` remains the temporary category for `THONG_TAM`.
- Store `penaltyWeightMultiplier` on `ShiftType` because the backend must persist the agreed weights now; AI-service will consume it in a later slice.
- Add `holidays` table and import holidays from backend resource JSON files. There is no manager-entered holiday CRUD in this slice.
- Do not introduce Flyway/Liquibase in this slice. The project currently uses `spring.jpa.hibernate.ddl-auto=update`; migration tooling is a separate decision.
- Do not add `startAt`, `endAt`, `workMinutes`, or transition-shift handling to `work_shifts` in this slice. That becomes the next scheduling-accuracy slice.

## Current Gaps

- `shift_types` lacks a stable shift code.
- `shift_types` lacks date applicability and penalty multiplier fields.
- Standard shift types are not seeded automatically.
- Holidays are not stored in DB and cannot include Lunar New Year, Hung Kings' Commemoration Day, substitute holidays, or year-specific public holidays.
- Work-shift creation does not reject a shift type on a wrong date.

## In Scope

### Shift Type Fields

Extend `ShiftType` with:

- `shiftCode`: stable unique code, max length 30.
- `applicableWeekdays`: comma-separated ISO day tokens, max length 40.
- `appliesOnHolidays`: boolean.
- `penaltyWeightMultiplier`: `BigDecimal`, default `1.00`.

Allowed weekday tokens:

- `MON`
- `TUE`
- `WED`
- `THU`
- `FRI`
- `SAT`
- `SUN`

Validation rules:

- `shiftCode` is required on create/update, unique, uppercase snake-case, and max length 30.
- `applicableWeekdays` is normalized to ISO order and duplicates are removed.
- Unknown weekday tokens are rejected with `400 BAD_REQUEST`.
- At least one weekday is required unless `appliesOnHolidays=true`.
- `durationHours` must be `> 0` and `<= 8.0`.
- `penaltyWeightMultiplier` must be between `0.00` and `9.99` inclusive.
- Existing `maxStaff >= minStaff`, status, quota category, and duplicate-name rules remain.

### Standard Shift Types

Seed six canonical shift types idempotently by `shiftCode`. If a row with the same `shiftCode` exists, update canonical fields.

| Shift code | Shift name | Group/category | Start | End | Duration | Overnight | Heavy | Applicable weekdays | Holidays | Multiplier |
|---|---|---|---:|---:|---:|---|---|---|---|---:|
| `THONG_TAM` | Thong tam | `BATHING` | 08:00 | 17:00 | 8.0 | false | true | MON,TUE,WED,THU,FRI | false | 1.00 |
| `NGOAI_GIO_1` | Ngoai gio 1 | `OVERTIME` | 17:00 | 00:30 | 7.5 | true | false | MON,TUE,WED,THU,FRI | false | 1.20 |
| `NGOAI_GIO_2` | Ngoai gio 2 | `OVERTIME` | 00:30 | 08:00 | 7.5 | false | true | MON,TUE,WED,THU,FRI | false | 1.50 |
| `TRUC_SANG` | Truc sang | `ON_CALL` | 08:00 | 16:00 | 8.0 | false | false | SAT,SUN | true | 1.00 |
| `TRUC_CHIEU` | Truc chieu | `ON_CALL` | 16:00 | 00:00 | 8.0 | true | false | SAT,SUN | true | 1.20 |
| `TRUC_DEM` | Truc dem | `ON_CALL` | 00:00 | 08:00 | 8.0 | false | true | SAT,SUN | true | 1.50 |

Notes:

- ASCII names match current code/test style. Localization can be handled later.
- `THONG_TAM`, `NGOAI_GIO_2`, and `TRUC_DEM` are `heavy=true` to support the existing heavy-shift penalty signal.
- Multiplier is persisted only. AI-service does not consume it in this slice.

### Holidays

Add a `Holiday` entity/table with:

- `holidayDate`: unique `LocalDate`.
- `holidayName`: display name.
- `holidayYear`: integer.
- `holidayType`: `PUBLIC_HOLIDAY`, `LUNAR_HOLIDAY`, `SUBSTITUTE_HOLIDAY`, `SPECIAL_HOLIDAY`.
- `sourceType`: `FIXED_RULE`, `ANNUAL_SEED`.
- `sourceReference`: optional string.
- `active`: boolean, default true.

Holiday source rules:

- Backend always seeds fixed-date holidays for relevant years:
  - 01/01
  - 30/04
  - 01/05
  - 02/09
- Backend imports year-specific files from `backend/src/main/resources/holidays/holidays-YYYY.json`.
- JSON dates are Gregorian/Vietnam dates in `YYYY-MM-DD` format. Do not store lunar-date strings and do not convert lunar to Gregorian in code.
- Import is idempotent and upserts by `holidayDate`.
- If annual JSON overlaps a fixed holiday, update the existing row instead of creating a duplicate.
- No `holiday_calendar_status` table in this slice. Completeness status and publish blocking are follow-ups.

Example JSON:

```json
[
  {
    "date": "2026-02-16",
    "name": "Nghi Tet Nguyen dan",
    "type": "LUNAR_HOLIDAY",
    "sourceReference": "Thong bao lich nghi nam 2026"
  }
]
```

### Day Classification

Add a backend helper/service that classifies a date:

1. `HOLIDAY` if an active holiday row exists for the date.
2. `WEEKEND` if Saturday or Sunday.
3. `WEEKDAY` otherwise.

Holiday has priority over weekend and weekday.

### Work Shift Creation Validation

When creating a `WorkShift`, backend validates the selected shift type against `workDate`:

- If day type is `HOLIDAY`, allow only shift types with `appliesOnHolidays=true`.
- If day type is `WEEKEND`, allow only shift types whose `applicableWeekdays` contains `SAT` or `SUN` for that date.
- If day type is `WEEKDAY`, allow only shift types whose `applicableWeekdays` contains that weekday.
- If invalid, reject with `400 BAD_REQUEST`, reason `Shift type is not applicable on this date`.

Examples:

- `THONG_TAM` on Monday is allowed.
- `THONG_TAM` on Saturday is rejected.
- `NGOAI_GIO_1` on Sunday is rejected.
- `TRUC_SANG` on Saturday is allowed.
- `TRUC_SANG` on `2026-09-02` is allowed if that holiday is seeded/imported.
- `TRUC_SANG` on a normal Tuesday is rejected.

### API Changes

Update shift type create/update/response DTOs with:

- `shiftCode`
- `applicableWeekdays`
- `appliesOnHolidays`
- `penaltyWeightMultiplier`

Create defaults:

- `applicableWeekdays`: default `MON,TUE,WED,THU,FRI,SAT,SUN` when null.
- `appliesOnHolidays`: default `false` when null.
- `penaltyWeightMultiplier`: default `1.00` when null.

Update keeps existing full-replacement style: new fields are required.

No holiday CRUD/API is added.

## Out Of Scope

- Frontend holiday UI.
- Manager-entered holiday CRUD.
- `holiday_calendar_status` and `HOLIDAY_CALENDAR_INCOMPLETE` blocking.
- `NEEDS_REVIEW` schedule state when holidays change.
- Transition-shift logic around `00:00`/`00:30`.
- Adding `startAt`, `endAt`, `workMinutes`, `isTransitionShift` to `WorkShift`.
- CP-SAT result validation before save/publish.
- Renaming/removing current `quotaCategory` values.
- Flyway/Liquibase migration framework.

## Testing

Add/extend backend tests:

- Shift type service/controller tests for `shiftCode`, applicability, multiplier, duration cap, and response fields.
- Standard shift seeder tests for create/update/idempotency and exact multipliers.
- Holiday seed/import tests for fixed holidays, JSON import, duplicate upsert, and invalid JSON/year validation.
- Day classification tests for holiday > weekend > weekday precedence.
- Work shift service tests for valid/invalid date applicability.
- Full backend suite must pass.

## Documentation Updates

Update:

- `docs/database-design-v2.md` with new `shift_types` and `holidays` columns.
- `docs/backend-ai-scheduling-contract.md` to note backend stores `shiftCode` and `penaltyWeightMultiplier`, but AI schema version `1.0` still uses the existing quota category contract and does not receive multiplier.
- `docs/project_plan.md` only if a listed backend/DB item is completed. Do not mark AI constraints complete.

## Follow-Ups

- Introduce holiday completeness tracking and publish blocking.
- Add transition-shift time windows on `work_shifts`.
- Add CP-SAT result validation before save/publish.
- Decide whether to replace `BATHING` with a clearer category name in backend + AI contract.
- Introduce Flyway/Liquibase if the project wants controlled production migrations.

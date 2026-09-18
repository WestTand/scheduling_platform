# Shift Rule Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend support for canonical shift codes, standard shift seeding, DB-backed holiday seeding/import, and work-shift date applicability validation.

**Architecture:** Extend `ShiftType` and add `Holiday` as JPA entities managed by existing Spring Data repositories. Keep validation in small backend helpers (`ShiftTypeRules`, `DayClassificationService`) so shift type APIs, seeders, and work-shift creation share the same rules. Because this project currently uses `spring.jpa.hibernate.ddl-auto=update` and not Flyway/Liquibase, add new persisted fields in a way that does not break existing databases; enforce required `shiftCode` at the service/API layer in this slice and make DB `NOT NULL` a later migration task. Seed standard shifts and holidays idempotently at startup; do not add frontend/API holiday management or AI-service scoring changes.

**Tech Stack:** Java 17, Spring Boot WebMVC/Data JPA, JUnit 5, AssertJ, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-18-shift-rule-foundation-design.md`

**Working directory:** All commands run from repo root (`scheduling_platform/`). Maven commands use `.\backend\mvnw.cmd -f backend/pom.xml`. In PowerShell, quote every `-D` argument, e.g. `"-Dtest=AdminShiftTypeServiceTest"`.

---

## File Map

- Modify: `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java` — add `shiftCode`, applicability fields, multiplier with ddl-auto-safe column nullability.
- Create: `backend/src/main/java/scheduling/scheduling/entity/Holiday.java` — holiday rows imported from fixed rules/JSON.
- Modify/Create repositories: `ShiftTypeRepository`, `HolidayRepository`.
- Modify shift type DTO/service/controller tests for new fields.
- Create helpers/services: `ShiftTypeRules`, `StandardShiftTypeSeeder`, `HolidaySeedService`, `DayClassificationService`.
- Modify `AdminWorkShiftService` to validate date applicability.
- Modify docs: `database-design-v2.md`, `backend-ai-scheduling-contract.md`.

---

### Task 1: Shift Type Canonical Fields

**Files:**
- Modify: `ShiftType.java`, shift type DTOs, `AdminShiftTypeService.java`, `ShiftTypeRepository.java`
- Test: `AdminShiftTypeServiceTest.java`, `AdminShiftTypeControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests covering:

- create defaults `applicableWeekdays=MON,TUE,WED,THU,FRI,SAT,SUN`, `appliesOnHolidays=false`, `penaltyWeightMultiplier=1.00`;
- create/update accepts and returns `shiftCode`;
- duplicate `shiftCode` rejects with `DuplicateResourceException`;
- lowercase, hyphenated, spaced, or over-30-character `shiftCode` rejects with `400`;
- weekday normalization (`fri,mon,mon` -> `MON,FRI`);
- invalid weekday token rejects `400`;
- blank weekday rejects unless `appliesOnHolidays=true`;
- `durationHours > 8.0` rejects `400`;
- multiplier outside `0.00..9.99` rejects `400`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftTypeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL due missing fields/accessors.

- [ ] **Step 2: Implement fields and validation**

Add to `ShiftType`:

```java
@Column(name = "shift_code", length = 30, unique = true)
private String shiftCode;

@Column(name = "applicable_weekdays", nullable = false, length = 40)
@Builder.Default
private String applicableWeekdays = "MON,TUE,WED,THU,FRI,SAT,SUN";

@Column(name = "applies_on_holidays", nullable = false)
@Builder.Default
private Boolean appliesOnHolidays = false;

@Column(name = "penalty_weight_multiplier", nullable = false, precision = 3, scale = 2)
@Builder.Default
private BigDecimal penaltyWeightMultiplier = BigDecimal.valueOf(1.00).setScale(2);
```

Do not mark `shift_code` `nullable=false` in this slice. Existing databases can already contain `shift_types` rows; with `ddl-auto=update`, adding a non-null column without a migration/backfill can fail startup. Enforce nonblank `shiftCode` in service/API validation for new writes, and document DB `NOT NULL` as a follow-up migration.

Update DTOs:

- `CreateShiftTypeRequest`: required `shiftCode` with `@NotBlank @Size(max = 30)`, optional `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier` with service defaults.
- `UpdateShiftTypeRequest`: required new fields.
- `AdminShiftTypeResponse`: include new fields.

Add `ShiftTypeRules` helper to normalize weekdays, validate duration and multiplier, validate `shiftCode`, and check weekday membership.

Validation details:

- `shiftCode` must match `^[A-Z][A-Z0-9_]{0,29}$`.
- `durationHours` must be `> 0` and `<= 8.0`.
- `penaltyWeightMultiplier` must be `>= 0.00` and `<= 9.99`.
- `penaltyWeightMultiplier` should be normalized to scale 2 before saving.
- `applicableWeekdays` should accept comma-separated tokens case-insensitively, trim spaces, remove duplicates, and output ISO order `MON,TUE,WED,THU,FRI,SAT,SUN`.
- Empty normalized weekdays are allowed only when `appliesOnHolidays=true`.

Update `ShiftTypeRepository` with:

```java
Optional<ShiftType> findByShiftCode(String shiftCode);

boolean existsByShiftCode(String shiftCode);

boolean existsByShiftCodeAndIdNot(String shiftCode, Long id);
```

Update `AdminShiftTypeService`:

- validate unique `shiftCode` on create and update;
- normalize and set new fields on create/update;
- preserve existing name/status/quota validations.

Run service and controller tests until PASS:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftTypeServiceTest,AdminShiftTypeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"
```

- [ ] **Step 3: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/entity/ShiftType.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java
git add backend/src/main/java/scheduling/scheduling/entity/ShiftType.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java
git commit -m "feat: add shift type canonical rule fields"
```

---

### Task 2: Standard Shift Type Seeder

**Files:**
- Create: `StandardShiftTypeSeeder.java`
- Modify: `ShiftTypeRepository.java`
- Test: `StandardShiftTypeSeederTest.java`

- [ ] **Step 1: Write failing seeder tests**

Test that startup seeder:

- creates six missing rows by `shiftCode`;
- updates an existing row by `shiftCode`;
- backfills a matching legacy row by `shiftName` when `shiftCode` is null;
- seeds all required `ShiftType` columns: `shiftName`, `startTime`, `endTime`, `durationHours`, `overnight`, `heavy`, `minStaff`, `maxStaff`, `quotaCategory`, `status`, `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier`;
- seeds exact multipliers: `1.00`, `1.20`, `1.50`, `1.00`, `1.20`, `1.50`;
- is idempotent on repeated `run()`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because seeder does not exist.

- [ ] **Step 2: Implement seeder**

Implement `ApplicationRunner` seeder using `ShiftTypeRepository.findByShiftCode(String shiftCode)` first. If not found, look for a legacy standard row by exact `shiftName` before creating a new row; this backfills existing local/dev databases without a migration.

Add to `ShiftTypeRepository` if it does not already exist:

```java
Optional<ShiftType> findByShiftName(String shiftName);
```

Canonical definitions must match the spec for shift-code/business-rule fields, plus explicit staffing defaults required by the existing `ShiftType` entity:

| Shift code | Shift name | Category | Start | End | Duration | Overnight | Heavy | Weekdays | Holidays | Multiplier | Min staff | Max staff | Status |
|---|---|---|---:|---:|---:|---|---|---|---|---:|---:|---:|---|
| `THONG_TAM` | `Thong tam` | `BATHING` | `08:00` | `17:00` | `8.0` | `false` | `true` | `MON,TUE,WED,THU,FRI` | `false` | `1.00` | `1` | `20` | `ACTIVE` |
| `NGOAI_GIO_1` | `Ngoai gio 1` | `OVERTIME` | `17:00` | `00:30` | `7.5` | `true` | `false` | `MON,TUE,WED,THU,FRI` | `false` | `1.20` | `1` | `20` | `ACTIVE` |
| `NGOAI_GIO_2` | `Ngoai gio 2` | `OVERTIME` | `00:30` | `08:00` | `7.5` | `false` | `true` | `MON,TUE,WED,THU,FRI` | `false` | `1.50` | `1` | `20` | `ACTIVE` |
| `TRUC_SANG` | `Truc sang` | `ON_CALL` | `08:00` | `16:00` | `8.0` | `false` | `false` | `SAT,SUN` | `true` | `1.00` | `1` | `20` | `ACTIVE` |
| `TRUC_CHIEU` | `Truc chieu` | `ON_CALL` | `16:00` | `00:00` | `8.0` | `true` | `false` | `SAT,SUN` | `true` | `1.20` | `1` | `20` | `ACTIVE` |
| `TRUC_DEM` | `Truc dem` | `ON_CALL` | `00:00` | `08:00` | `8.0` | `false` | `true` | `SAT,SUN` | `true` | `1.50` | `1` | `20` | `ACTIVE` |

Seeder update behavior:

- For rows found by `shiftCode`, update every canonical field in the table above.
- For legacy rows found by exact `shiftName`, set `shiftCode` and update every canonical field in the table above.
- For missing rows, create a new `ShiftType` with every field in the table above.
- Keep `minStaff=1` and `maxStaff=20` for all seeded rows in this slice; specific daily staffing remains controlled by `WorkShift.requiredStaff`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest,SchedulingApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/StandardShiftTypeSeederTest.java
git add backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/StandardShiftTypeSeederTest.java
git commit -m "feat: seed canonical shift types"
```

---

### Task 3: Holiday Entity And JSON Seeder

**Files:**
- Create: `Holiday.java`, `HolidayRepository.java`, `HolidaySeedService.java`
- Create test: `HolidaySeedServiceTest.java`
- Add resource: `backend/src/main/resources/holidays/holidays-2026.json`

- [ ] **Step 1: Write failing holiday seed tests**

Tests cover:

- fixed holidays are generated for a requested year;
- JSON file `holidays-2026.json` imports annual holidays;
- duplicate date upserts existing row;
- invalid year/date in JSON rejects with `IllegalStateException`;
- repeated seed does not create duplicate rows.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=HolidaySeedServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because classes do not exist.

- [ ] **Step 2: Implement holiday model/seeder**

Create `Holiday` entity with fields from spec and `HolidayRepository` methods:

- `Optional<Holiday> findByHolidayDate(LocalDate holidayDate)`
- `boolean existsByHolidayDateAndActiveTrue(LocalDate holidayDate)`

Implement `HolidaySeedService` as `ApplicationRunner`:

- expose package-private methods for tests: `seedFixedHolidaysForYear(int year)` and `importAnnualHolidayResource(String filename, Resource resource)`;
- `run(ApplicationArguments args)` seeds fixed holidays for the current year and next year;
- read all matching `classpath:holidays/holidays-*.json` files;
- validate JSON date year matches filename;
- throw `IllegalStateException` when filename year cannot be parsed, a JSON date cannot be parsed, JSON date year differs from filename year, or holiday type is unknown;
- upsert by `holidayDate`;
- set `active=true`.

Add `backend/src/main/resources/holidays/holidays-2026.json` with a small valid example including Tet/Gio To/substitute holiday dates from `docs/Đặc tả_Phát.txt`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=HolidaySeedServiceTest,SchedulingApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/entity/Holiday.java backend/src/main/java/scheduling/scheduling/repository/HolidayRepository.java backend/src/main/java/scheduling/scheduling/admin/HolidaySeedService.java backend/src/test/java/scheduling/scheduling/admin/HolidaySeedServiceTest.java backend/src/main/resources/holidays/holidays-2026.json
git add backend/src/main/java/scheduling/scheduling/entity/Holiday.java backend/src/main/java/scheduling/scheduling/repository/HolidayRepository.java backend/src/main/java/scheduling/scheduling/admin/HolidaySeedService.java backend/src/test/java/scheduling/scheduling/admin/HolidaySeedServiceTest.java backend/src/main/resources/holidays/holidays-2026.json
git commit -m "feat: seed holiday calendar from json"
```

---

### Task 4: Day Classification And Work Shift Validation

**Files:**
- Create: `DayClassificationService.java`
- Modify: `AdminWorkShiftService.java`
- Test: `AdminWorkShiftServiceTest.java`, new day classification test if useful.

- [ ] **Step 1: Write failing tests**

Add tests for:

- holiday date is classified before weekend;
- weekday shift allowed on weekday;
- weekday shift rejected on weekend;
- on-call shift allowed on weekend;
- on-call shift allowed on active holiday weekday;
- on-call shift rejected on normal weekday.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL until service constructor/validation updated.

- [ ] **Step 2: Implement validation**

Create day classification enum/helper and inject into `AdminWorkShiftService`.

Validation rule:

- `HOLIDAY`: require `shiftType.appliesOnHolidays == true`.
- `WEEKEND`/`WEEKDAY`: require `ShiftTypeRules.includesWeekday(...)` for actual weekday.
- Error reason: `Shift type is not applicable on this date`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git status --short
git diff -- backend/src/main/java/scheduling/scheduling/admin/DayClassificationService.java backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java
git add backend/src/main/java/scheduling/scheduling/admin/DayClassificationService.java backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java
git commit -m "feat: validate work shift day applicability"
```

---

### Task 5: Documentation And Verification

**Files:**
- Modify: `docs/database-design-v2.md`
- Modify: `docs/backend-ai-scheduling-contract.md`

- [ ] **Step 1: Update docs**

Update database docs for:

- `shift_code`, applicability fields, multiplier;
- note that `shift_code` is application-required now but DB-nullable until a later migration can safely backfill and add `NOT NULL`;
- `holidays` table.

Update AI contract docs:

- backend stores `shiftCode` and `penaltyWeightMultiplier`;
- AI schema `1.0` does not receive multiplier yet;
- quota category contract remains unchanged for this slice.

- [ ] **Step 2: Full verification**

Run: `.\backend\mvnw.cmd -f backend/pom.xml clean test`
Expected: all backend tests PASS.

Run: `git diff --check`
Expected: no output.

Run: `git status --short`
Expected: only intentional documentation changes are shown before staging. Do not stage unrelated untracked files such as external source documents unless the user explicitly asks.

- [ ] **Step 3: Commit docs**

```bash
git diff -- docs/database-design-v2.md docs/backend-ai-scheduling-contract.md
git add docs/database-design-v2.md docs/backend-ai-scheduling-contract.md
git commit -m "docs: update shift calendar data model"
```

---

## Spec Coverage

- Canonical `shiftCode`: Task 1.
- Six standard shifts and multipliers: Task 2.
- Holiday DB + JSON import: Task 3.
- Holiday priority and work-shift applicability: Task 4.
- Docs/full verification: Task 5.
- Explicitly deferred CP-SAT validation, transition windows, Flyway, and holiday completeness: no task implements them.

## Self-Review

- Spec coverage: every in-scope item maps to a task.
- Placeholder scan: no TBD/TODO; deferred features are named in the spec and intentionally not implemented.
- Type consistency: `shiftCode`, `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier`, `Holiday`, and `holidayDate` names are consistent across tasks.

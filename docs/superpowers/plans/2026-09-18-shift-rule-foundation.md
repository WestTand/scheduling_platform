# Shift Rule Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend support for canonical shift codes, standard shift seeding, DB-backed holiday seeding/import, and work-shift date applicability validation.

**Architecture:** Extend `ShiftType` and add `Holiday` as JPA entities managed by existing Spring Data repositories. Keep validation in small backend helpers (`ShiftTypeRules`, `DayClassificationService`) so shift type APIs, seeders, and work-shift creation share the same rules. Seed standard shifts and holidays idempotently at startup; do not add frontend/API holiday management or AI-service scoring changes.

**Tech Stack:** Java 17, Spring Boot WebMVC/Data JPA, JUnit 5, AssertJ, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-18-shift-rule-foundation-design.md`

**Working directory:** All commands run from repo root (`scheduling_platform/`). Maven commands use `.\backend\mvnw.cmd -f backend/pom.xml`. In PowerShell, quote every `-D` argument, e.g. `"-Dtest=AdminShiftTypeServiceTest"`.

---

## File Map

- Modify: `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java` — add `shiftCode`, applicability fields, multiplier.
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
@Column(name = "shift_code", nullable = false, length = 30, unique = true)
private String shiftCode;
```

and applicability/multiplier fields from the spec.

Update DTOs:

- `CreateShiftTypeRequest`: optional `shiftCode`, `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier` except `shiftCode` should be `@NotBlank`.
- `UpdateShiftTypeRequest`: required new fields.
- `AdminShiftTypeResponse`: include new fields.

Add `ShiftTypeRules` helper to normalize weekdays, validate duration and multiplier, and check weekday membership.

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
git add backend/src/main/java/scheduling/scheduling/entity/ShiftType.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java
git commit -m "feat: add shift type canonical rule fields"
```

---

### Task 2: Standard Shift Type Seeder

**Files:**
- Create: `StandardShiftTypeSeeder.java`
- Test: `StandardShiftTypeSeederTest.java`

- [ ] **Step 1: Write failing seeder tests**

Test that startup seeder:

- creates six missing rows by `shiftCode`;
- updates an existing row by `shiftCode`;
- seeds exact multipliers: `1.00`, `1.20`, `1.50`, `1.00`, `1.20`, `1.50`;
- is idempotent on repeated `run()`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because seeder does not exist.

- [ ] **Step 2: Implement seeder**

Implement `ApplicationRunner` seeder using `ShiftTypeRepository.findByShiftCode(String shiftCode)`.

Canonical definitions must match spec exactly:

- `THONG_TAM`: `BATHING`, `1.00`, weekdays `MON,TUE,WED,THU,FRI`.
- `NGOAI_GIO_1`: `OVERTIME`, `1.20`, weekdays `MON,TUE,WED,THU,FRI`.
- `NGOAI_GIO_2`: `OVERTIME`, `1.50`, weekdays `MON,TUE,WED,THU,FRI`.
- `TRUC_SANG`: `ON_CALL`, `1.00`, weekdays `SAT,SUN`, holidays true.
- `TRUC_CHIEU`: `ON_CALL`, `1.20`, weekdays `SAT,SUN`, holidays true.
- `TRUC_DEM`: `ON_CALL`, `1.50`, weekdays `SAT,SUN`, holidays true.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest,SchedulingApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java backend/src/test/java/scheduling/scheduling/admin/StandardShiftTypeSeederTest.java
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
- invalid year/date in JSON rejects or skips with a deterministic exception;
- repeated seed does not create duplicate rows.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=HolidaySeedServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because classes do not exist.

- [ ] **Step 2: Implement holiday model/seeder**

Create `Holiday` entity with fields from spec and `HolidayRepository` methods:

- `Optional<Holiday> findByHolidayDate(LocalDate holidayDate)`
- `boolean existsByHolidayDateAndActiveTrue(LocalDate holidayDate)`

Implement `HolidaySeedService` as `ApplicationRunner`:

- seed fixed holidays for the current year and next year;
- read all matching `classpath:holidays/holidays-*.json` files;
- validate JSON date year matches filename;
- upsert by `holidayDate`;
- set `active=true`.

Add `backend/src/main/resources/holidays/holidays-2026.json` with a small valid example including Tet/Gio To/substitute holiday dates from `docs/Đặc tả_Phát.txt`.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=HolidaySeedServiceTest,SchedulingApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
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

- [ ] **Step 3: Commit docs**

```bash
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

# Shift Rule Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend backend shift type data, validation, and standard seeding so work shifts follow the fixed shift-rule document.

**Architecture:** Add three fields to `ShiftType` and expose them through existing shift-type APIs. Centralize weekday normalization and fixed-date holiday detection in small helpers, then inject the holiday helper into work-shift creation validation. Seed the six canonical shift types idempotently at startup without adding migration tooling or holiday tables.

**Tech Stack:** Java 17, Spring Boot WebMVC/Data JPA, JUnit 5, AssertJ, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-18-shift-rule-foundation-design.md`

**Working directory:** All commands run from repo root (`scheduling_platform/`). Maven commands use `.ackend

---

## File Map

- Modify: `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java` — add applicability and multiplier fields.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java` — expose optional create fields.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java` — expose required update fields.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java` — return new fields.
- Create: `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java` — normalize weekdays, validate duration/multiplier.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java` — apply rule helper on create/update.
- Create: `backend/src/main/java/scheduling/scheduling/calendar/FixedHolidayCalendar.java` — fixed-date holiday helper.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java` — validate `workDate` against shift type applicability.
- Create: `backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java` — idempotently seed six canonical shift types.
- Modify tests: `AdminShiftTypeServiceTest`, `AdminShiftTypeControllerTest`, `AdminWorkShiftServiceTest`.
- Create tests: `FixedHolidayCalendarTest`, `StandardShiftTypeSeederTest`.
- Modify docs: `docs/database-design-v2.md`, `docs/backend-ai-scheduling-contract.md`.

---

### Task 1: Shift Type Fields And Validation

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Add these tests to `AdminShiftTypeServiceTest`:

```java
    @Test
    void createShiftTypeDefaultsApplicabilityAndMultiplier() {
        CreateShiftTypeRequest request = createRequest("Flexible", null, null, 1, 3, null);
        when(shiftTypeRepository.existsByShiftName("Flexible")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.applicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI,SAT,SUN");
        assertThat(response.appliesOnHolidays()).isFalse();
        assertThat(response.penaltyWeightMultiplier()).isEqualByComparingTo("1.00");
    }

    @Test
    void createShiftTypeNormalizesApplicability() {
        CreateShiftTypeRequest request = new CreateShiftTypeRequest(
                "Weekday", LocalTime.of(8, 0), LocalTime.of(16, 0), BigDecimal.valueOf(8.0),
                false, false, 1, 3, "BATHING", "fri,mon,mon", false, new BigDecimal("1.25"));
        when(shiftTypeRepository.existsByShiftName("Weekday")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.applicableWeekdays()).isEqualTo("MON,FRI");
        assertThat(response.appliesOnHolidays()).isFalse();
        assertThat(response.penaltyWeightMultiplier()).isEqualByComparingTo("1.25");
    }

    @Test
    void createShiftTypeAllowsHolidayOnlyApplicability() {
        CreateShiftTypeRequest request = new CreateShiftTypeRequest(
                "Holiday", LocalTime.of(8, 0), LocalTime.of(16, 0), BigDecimal.valueOf(8.0),
                false, false, 1, 3, "ON_CALL", " ", true, BigDecimal.ONE);
        when(shiftTypeRepository.existsByShiftName("Holiday")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.applicableWeekdays()).isEmpty();
        assertThat(response.appliesOnHolidays()).isTrue();
    }

    @Test
    void createShiftTypeRejectsInvalidApplicabilityDurationAndMultiplier() {
        assertBadRequest(() -> service.createShiftType(new CreateShiftTypeRequest(
                "Bad day", LocalTime.of(8, 0), LocalTime.of(16, 0), BigDecimal.valueOf(8.0),
                false, false, 1, 3, "REGULAR", "MON,FUNDAY", false, BigDecimal.ONE)));
        assertBadRequest(() -> service.createShiftType(new CreateShiftTypeRequest(
                "No days", LocalTime.of(8, 0), LocalTime.of(16, 0), BigDecimal.valueOf(8.0),
                false, false, 1, 3, "REGULAR", " ", false, BigDecimal.ONE)));
        assertBadRequest(() -> service.createShiftType(new CreateShiftTypeRequest(
                "Too long", LocalTime.of(8, 0), LocalTime.of(18, 0), BigDecimal.valueOf(8.5),
                false, false, 1, 3, "REGULAR", "MON", false, BigDecimal.ONE)));
        assertBadRequest(() -> service.createShiftType(new CreateShiftTypeRequest(
                "Bad multiplier", LocalTime.of(8, 0), LocalTime.of(16, 0), BigDecimal.valueOf(8.0),
                false, false, 1, 3, "REGULAR", "MON", false, new BigDecimal("10.00"))));
    }
```

Add this helper to `AdminShiftTypeServiceTest`:

```java
    private static void assertBadRequest(ThrowingServiceCall call) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @FunctionalInterface
    private interface ThrowingServiceCall {
        void execute();
    }
```

Update existing `createRequest`, `updateRequest`, and `shiftType` helpers to pass/set `applicableWeekdays`, `appliesOnHolidays`, and `penaltyWeightMultiplier` after implementation changes. Keep existing tests' intent unchanged.

- [ ] **Step 2: Run service tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftTypeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL with compilation errors for new DTO/response accessors.

- [ ] **Step 3: Implement entity, DTOs, helper, and service validation**

In `ShiftType.java`, add imports and fields:

```java
import java.math.BigDecimal;
```

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

Update `CreateShiftTypeRequest` record signature to:

```java
public record CreateShiftTypeRequest(
        @NotBlank @Size(max = 50) String shiftName,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin("0.1") @Digits(integer = 3, fraction = 1) BigDecimal durationHours,
        Boolean overnight,
        Boolean heavy,
        @NotNull @Min(1) Integer minStaff,
        @NotNull @Min(1) Integer maxStaff,
        @Size(max = 20) String quotaCategory,
        @Size(max = 40) String applicableWeekdays,
        Boolean appliesOnHolidays,
        @Digits(integer = 2, fraction = 2) BigDecimal penaltyWeightMultiplier) {
}
```

Update `UpdateShiftTypeRequest` record signature to:

```java
public record UpdateShiftTypeRequest(
        @NotBlank @Size(max = 50) String shiftName,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin("0.1") @Digits(integer = 3, fraction = 1) BigDecimal durationHours,
        @NotNull Boolean overnight,
        @NotNull Boolean heavy,
        @NotNull @Min(1) Integer minStaff,
        @NotNull @Min(1) Integer maxStaff,
        @NotBlank @Size(max = 20) @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @NotBlank @Size(max = 20) String quotaCategory,
        @NotNull @Size(max = 40) String applicableWeekdays,
        @NotNull Boolean appliesOnHolidays,
        @NotNull @Digits(integer = 2, fraction = 2) BigDecimal penaltyWeightMultiplier) {
}
```

Update `AdminShiftTypeResponse` record to add fields after `quotaCategory`:

```java
        String quotaCategory,
        String applicableWeekdays,
        Boolean appliesOnHolidays,
        BigDecimal penaltyWeightMultiplier,
        String status) {
```

and pass:

```java
                shiftType.getQuotaCategory(),
                shiftType.getApplicableWeekdays(),
                shiftType.getAppliesOnHolidays(),
                shiftType.getPenaltyWeightMultiplier(),
                shiftType.getStatus());
```

Create `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java`:

```java
package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

final class ShiftTypeRules {

    static final String ALL_WEEKDAYS = "MON,TUE,WED,THU,FRI,SAT,SUN";
    private static final BigDecimal MAX_DURATION_HOURS = new BigDecimal("8.0");
    private static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal MIN_MULTIPLIER = new BigDecimal("0.00");
    private static final BigDecimal MAX_MULTIPLIER = new BigDecimal("9.99");

    private ShiftTypeRules() {
    }

    static String normalizeApplicableWeekdays(String value, Boolean appliesOnHolidays, boolean defaultWhenNull) {
        if (value == null && defaultWhenNull) {
            return ALL_WEEKDAYS;
        }
        boolean holidayOnlyAllowed = Boolean.TRUE.equals(appliesOnHolidays);
        if (!StringUtils.hasText(value)) {
            if (holidayOnlyAllowed) {
                return "";
            }
            throw invalidRequest();
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : value.split(",")) {
            String normalized = token.trim().toUpperCase();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            days.add(toDayOfWeek(normalized));
        }
        if (days.isEmpty() && !holidayOnlyAllowed) {
            throw invalidRequest();
        }
        return days.stream()
                .map(ShiftTypeRules::toToken)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    static BigDecimal normalizePenaltyWeightMultiplier(BigDecimal value) {
        BigDecimal normalized = value == null ? DEFAULT_MULTIPLIER : value;
        if (normalized.compareTo(MIN_MULTIPLIER) < 0 || normalized.compareTo(MAX_MULTIPLIER) > 0) {
            throw invalidRequest();
        }
        return normalized;
    }

    static void validateDuration(BigDecimal durationHours) {
        if (durationHours == null || durationHours.compareTo(BigDecimal.ZERO) <= 0
                || durationHours.compareTo(MAX_DURATION_HOURS) > 0) {
            throw invalidRequest();
        }
    }

    static boolean includesWeekday(String applicableWeekdays, DayOfWeek dayOfWeek) {
        String token = toToken(dayOfWeek);
        for (String candidate : applicableWeekdays.split(",")) {
            if (token.equals(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private static DayOfWeek toDayOfWeek(String token) {
        return switch (token) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw invalidRequest();
        };
    }

    private static String toToken(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift type request");
    }
}
```

In `AdminShiftTypeService`, call `ShiftTypeRules.validateDuration(request.durationHours())`, normalize fields, set them on builder/update:

```java
        ShiftTypeRules.validateDuration(request.durationHours());
        String applicableWeekdays = ShiftTypeRules.normalizeApplicableWeekdays(
                request.applicableWeekdays(), request.appliesOnHolidays(), true);
        Boolean appliesOnHolidays = Boolean.TRUE.equals(request.appliesOnHolidays());
        BigDecimal penaltyWeightMultiplier = ShiftTypeRules.normalizePenaltyWeightMultiplier(
                request.penaltyWeightMultiplier());
```

For update use `defaultWhenNull=false` and required fields.

- [ ] **Step 4: Run service tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftTypeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 5: Update controller tests and verify**

Update `AdminShiftTypeControllerTest.response()` constructor to include:

```java
                "OVERTIME",
                "MON,TUE,WED,THU,FRI",
                false,
                BigDecimal.ONE,
                "ACTIVE");
```

Add JSON fields to create/update request bodies and assertions:

```json
"applicableWeekdays": "MON,TUE,WED,THU,FRI",
"appliesOnHolidays": false,
"penaltyWeightMultiplier": 1.00
```

Assert captured request fields:

```java
assertThat(request.applicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI");
assertThat(request.appliesOnHolidays()).isFalse();
assertThat(request.penaltyWeightMultiplier()).isEqualByComparingTo("1.00");
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftTypeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/entity/ShiftType.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java
git commit -m "feat: extend shift type applicability fields"
```

---

### Task 2: Fixed Holiday And Work Shift Applicability

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/calendar/FixedHolidayCalendar.java`
- Create: `backend/src/test/java/scheduling/scheduling/calendar/FixedHolidayCalendarTest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java`

- [ ] **Step 1: Write failing holiday tests**

Create `FixedHolidayCalendarTest.java`:

```java
package scheduling.scheduling.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FixedHolidayCalendarTest {

    private final FixedHolidayCalendar calendar = new FixedHolidayCalendar();

    @Test
    void recognizesFixedDateHolidaysInAnyYear() {
        assertThat(calendar.isHoliday(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2027, 4, 30))).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2028, 5, 1))).isTrue();
        assertThat(calendar.isHoliday(LocalDate.of(2029, 9, 2))).isTrue();
    }

    @Test
    void rejectsNormalDays() {
        assertThat(calendar.isHoliday(LocalDate.of(2026, 9, 3))).isFalse();
    }
}
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=FixedHolidayCalendarTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because `FixedHolidayCalendar` does not exist.

- [ ] **Step 2: Implement fixed holiday calendar**

Create `FixedHolidayCalendar.java`:

```java
package scheduling.scheduling.calendar;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class FixedHolidayCalendar {

    public boolean isHoliday(LocalDate date) {
        return date != null && switch (date.getMonth()) {
            case JANUARY -> date.getDayOfMonth() == 1;
            case APRIL -> date.getDayOfMonth() == 30;
            case MAY -> date.getDayOfMonth() == 1;
            case SEPTEMBER -> date.getDayOfMonth() == 2;
            default -> false;
        };
    }
}
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=FixedHolidayCalendarTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 3: Write failing work shift applicability tests**

Update `AdminWorkShiftServiceTest` setup to construct the service with `new FixedHolidayCalendar()`:

```java
    private final AdminWorkShiftService service = new AdminWorkShiftService(
            workShiftRepository, schedulePeriodRepository, shiftTypeRepository, accountRepository,
            new FixedHolidayCalendar());
```

Add import `scheduling.scheduling.calendar.FixedHolidayCalendar`.

Add tests:

```java
    @Test
    void createWorkShiftRejectsWeekdayShiftOnWeekend() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 19), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftAllowsOnCallShiftOnWeekendAndHoliday() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftType onCall = activeShiftType();
        onCall.setQuotaCategory("ON_CALL");
        onCall.setApplicableWeekdays("SAT,SUN");
        onCall.setAppliesOnHolidays(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(onCall));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 19), 1, null)).workDate())
                .isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 2), 1, null)).workDate())
                .isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    void createWorkShiftRejectsOnCallShiftOnNormalWeekday() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftType onCall = activeShiftType();
        onCall.setQuotaCategory("ON_CALL");
        onCall.setApplicableWeekdays("SAT,SUN");
        onCall.setAppliesOnHolidays(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(onCall));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 15), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }
```

Update `activeShiftType()` helper to set:

```java
                .applicableWeekdays("MON,TUE,WED,THU,FRI")
                .appliesOnHolidays(false)
                .penaltyWeightMultiplier(BigDecimal.ONE)
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because constructor/service validation not updated.

- [ ] **Step 4: Implement work shift date validation**

Modify `AdminWorkShiftService` constructor to include `FixedHolidayCalendar fixedHolidayCalendar`; assign field.

After `validateDateInPeriod(request.workDate(), period);`, add:

```java
        validateShiftTypeApplicability(request.workDate(), shiftType);
```

Add private method:

```java
    private void validateShiftTypeApplicability(LocalDate workDate, ShiftType shiftType) {
        if (fixedHolidayCalendar.isHoliday(workDate) && Boolean.TRUE.equals(shiftType.getAppliesOnHolidays())) {
            return;
        }
        if (ShiftTypeRules.includesWeekday(shiftType.getApplicableWeekdays(), workDate.getDayOfWeek())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shift type is not applicable on this date");
    }
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminWorkShiftServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/calendar/FixedHolidayCalendar.java backend/src/test/java/scheduling/scheduling/calendar/FixedHolidayCalendarTest.java backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java
git commit -m "feat: validate work shift date applicability"
```

---

### Task 3: Standard Shift Type Seeder

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/StandardShiftTypeSeederTest.java`

- [ ] **Step 1: Write failing seeder tests**

Create `StandardShiftTypeSeederTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

class StandardShiftTypeSeederTest {

    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final StandardShiftTypeSeeder seeder = new StandardShiftTypeSeeder(shiftTypeRepository);

    @Test
    void createsMissingStandardShiftTypes() {
        when(shiftTypeRepository.findByShiftName(any())).thenReturn(Optional.empty());
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        verify(shiftTypeRepository).findByShiftName("Thong tam");
        verify(shiftTypeRepository).findByShiftName("Ngoai gio 1");
        verify(shiftTypeRepository).findByShiftName("Ngoai gio 2");
        verify(shiftTypeRepository).findByShiftName("Truc sang");
        verify(shiftTypeRepository).findByShiftName("Truc chieu");
        verify(shiftTypeRepository).findByShiftName("Truc dem");
        verify(shiftTypeRepository, org.mockito.Mockito.times(6)).save(any(ShiftType.class));
    }

    @Test
    void updatesExistingStandardShiftTypeByName() {
        ShiftType existing = ShiftType.builder()
                .shiftName("Thong tam")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(15, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(1)
                .quotaCategory("REGULAR")
                .applicableWeekdays("SAT")
                .appliesOnHolidays(true)
                .penaltyWeightMultiplier(new BigDecimal("2.00"))
                .status("INACTIVE")
                .build();
        when(shiftTypeRepository.findByShiftName("Thong tam")).thenReturn(Optional.of(existing));
        when(shiftTypeRepository.findByShiftName(org.mockito.ArgumentMatchers.not(org.mockito.ArgumentMatchers.eq("Thong tam"))))
                .thenReturn(Optional.empty());
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(existing.getDurationHours()).isEqualByComparingTo("8.0");
        assertThat(existing.getHeavy()).isTrue();
        assertThat(existing.getQuotaCategory()).isEqualTo("BATHING");
        assertThat(existing.getApplicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI");
        assertThat(existing.getAppliesOnHolidays()).isFalse();
        assertThat(existing.getPenaltyWeightMultiplier()).isEqualByComparingTo("1.00");
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void seedsNightAndLateShiftMultipliers() {
        when(shiftTypeRepository.findByShiftName(any())).thenReturn(Optional.empty());
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        org.mockito.ArgumentCaptor<ShiftType> captor = org.mockito.ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, org.mockito.Mockito.times(6)).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(shiftType -> "Ngoai gio 1".equals(shiftType.getShiftName()))
                .singleElement()
                .extracting(ShiftType::getPenaltyWeightMultiplier)
                .isEqualTo(new BigDecimal("1.20"));
        assertThat(captor.getAllValues())
                .filteredOn(shiftType -> "Ngoai gio 2".equals(shiftType.getShiftName()))
                .singleElement()
                .extracting(ShiftType::getPenaltyWeightMultiplier)
                .isEqualTo(new BigDecimal("1.50"));
        assertThat(captor.getAllValues())
                .filteredOn(shiftType -> "Truc chieu".equals(shiftType.getShiftName()))
                .singleElement()
                .extracting(ShiftType::getPenaltyWeightMultiplier)
                .isEqualTo(new BigDecimal("1.20"));
        assertThat(captor.getAllValues())
                .filteredOn(shiftType -> "Truc dem".equals(shiftType.getShiftName()))
                .singleElement()
                .extracting(ShiftType::getPenaltyWeightMultiplier)
                .isEqualTo(new BigDecimal("1.50"));
    }
}
```

Add repository method to `ShiftTypeRepository` in the implementation step if it does not exist.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL because seeder/repository method does not exist.

- [ ] **Step 2: Implement repository method and seeder**

In `ShiftTypeRepository`, add:

```java
    Optional<ShiftType> findByShiftName(String shiftName);
```

Create `StandardShiftTypeSeeder.java`:

```java
package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Component
public class StandardShiftTypeSeeder implements ApplicationRunner {

    private final ShiftTypeRepository shiftTypeRepository;

    public StandardShiftTypeSeeder(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        run();
    }

    @Transactional
    void run() {
        for (StandardShift definition : definitions()) {
            ShiftType shiftType = shiftTypeRepository.findByShiftName(definition.name())
                    .orElseGet(() -> ShiftType.builder().shiftName(definition.name()).build());
            apply(definition, shiftType);
            shiftTypeRepository.save(shiftType);
        }
    }

    private static void apply(StandardShift definition, ShiftType shiftType) {
        shiftType.setShiftName(definition.name());
        shiftType.setStartTime(definition.start());
        shiftType.setEndTime(definition.end());
        shiftType.setDurationHours(definition.durationHours());
        shiftType.setOvernight(definition.overnight());
        shiftType.setHeavy(definition.heavy());
        shiftType.setMinStaff(1);
        shiftType.setMaxStaff(3);
        shiftType.setQuotaCategory(definition.quotaCategory());
        shiftType.setApplicableWeekdays(definition.applicableWeekdays());
        shiftType.setAppliesOnHolidays(definition.appliesOnHolidays());
        shiftType.setPenaltyWeightMultiplier(definition.penaltyWeightMultiplier());
        shiftType.setStatus("ACTIVE");
    }

    private static List<StandardShift> definitions() {
        return List.of(
                new StandardShift("Thong tam", LocalTime.of(8, 0), LocalTime.of(17, 0), new BigDecimal("8.0"), false, true, "BATHING", "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.00")),
                new StandardShift("Ngoai gio 1", LocalTime.of(17, 0), LocalTime.of(0, 30), new BigDecimal("7.5"), true, false, "OVERTIME", "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.20")),
                new StandardShift("Ngoai gio 2", LocalTime.of(0, 30), LocalTime.of(8, 0), new BigDecimal("7.5"), false, true, "OVERTIME", "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.50")),
                new StandardShift("Truc sang", LocalTime.of(8, 0), LocalTime.of(16, 0), new BigDecimal("8.0"), false, false, "ON_CALL", "SAT,SUN", true, new BigDecimal("1.00")),
                new StandardShift("Truc chieu", LocalTime.of(16, 0), LocalTime.of(0, 0), new BigDecimal("8.0"), true, false, "ON_CALL", "SAT,SUN", true, new BigDecimal("1.20")),
                new StandardShift("Truc dem", LocalTime.of(0, 0), LocalTime.of(8, 0), new BigDecimal("8.0"), false, true, "ON_CALL", "SAT,SUN", true, new BigDecimal("1.50")));
    }

    private record StandardShift(
            String name,
            LocalTime start,
            LocalTime end,
            BigDecimal durationHours,
            boolean overnight,
            boolean heavy,
            String quotaCategory,
            String applicableWeekdays,
            boolean appliesOnHolidays,
            BigDecimal penaltyWeightMultiplier) {
    }
}
```

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 3: Run related tests and commit**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=StandardShiftTypeSeederTest,SchedulingApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

```bash
git add backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/test/java/scheduling/scheduling/admin/StandardShiftTypeSeederTest.java
git commit -m "feat: seed standard shift types"
```

---

### Task 4: Documentation And Full Verification

**Files:**
- Modify: `docs/database-design-v2.md`
- Modify: `docs/backend-ai-scheduling-contract.md`

- [ ] **Step 1: Run full backend suite before docs status**

Run: `.\backend\mvnw.cmd -f backend/pom.xml clean test`
Expected: all tests PASS with 0 failures/errors.

- [ ] **Step 2: Update docs**

In `docs/database-design-v2.md`, update `shift_types` section to include:

```markdown
| applicable_weekdays | VARCHAR(40) | NOT NULL, DEFAULT 'MON,TUE,WED,THU,FRI,SAT,SUN' | ISO weekday tokens allowed for this shift type |
| applies_on_holidays | BOOLEAN | NOT NULL, DEFAULT FALSE | Allows this shift type on fixed-date holidays |
| penalty_weight_multiplier | NUMERIC(4,2) | NOT NULL, DEFAULT 1.00 | Reserved for future AI penalty weighting |
```

In `docs/backend-ai-scheduling-contract.md`, add a note in the `shiftTypes` section:

```markdown
Backend `shift_types` also stores `applicableWeekdays`, `appliesOnHolidays`, and `penaltyWeightMultiplier`. These fields are used by backend work-shift validation and are not sent to AI-service in schema version `1.0`.
```

- [ ] **Step 3: Check whitespace and commit docs**

Run: `git diff --check`
Expected: no output.

```bash
git add docs/database-design-v2.md docs/backend-ai-scheduling-contract.md
git commit -m "docs: update shift rule data model"
```

- [ ] **Step 4: Final verification**

Run: `.\backend\mvnw.cmd -f backend/pom.xml clean test`
Expected: all tests PASS. Report final test summary.

Run: `git status --short`
Expected: no unexpected code/doc changes besides already committed work. If `docs/quy_dinh_ca_lam_viec.md` remains untracked, report it as pre-existing/unrelated and do not commit it unless explicitly requested.

---

## Spec Coverage

- Fixed-date holiday calendar: Task 2.
- Shift type fields (`applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier`): Task 1.
- Duration <= 8 validation: Task 1.
- Work-shift date applicability validation: Task 2.
- Standard six shift-type seed: Task 3.
- No holiday table/API and no AI scoring change: enforced by scope; no task adds them.
- Docs update: Task 4.
- Full backend verification: Task 4.

## Self-Review

- Spec coverage: every in-scope requirement maps to a task.
- Placeholder scan: no TBD/TODO placeholders; snippets include concrete class, method, and command names.
- Type consistency: DTO fields are `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier`; entity fields and response accessors use the same names; `FixedHolidayCalendar.isHoliday(LocalDate)` is used by `AdminWorkShiftService`.

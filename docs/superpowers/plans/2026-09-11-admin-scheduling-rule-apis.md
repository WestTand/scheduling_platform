# Admin Scheduling Rule APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend admin APIs to read and update global scheduling rule configuration for later manual scheduling and OR-Tools AI scheduling.

**Architecture:** Keep this as a focused admin backend slice. Store rules in existing `SchedulingRule` rows keyed by stable `ruleCode`, expose a grouped DTO (`hardRules`, `quotas`, `softWeights`), and do not change the database schema. Use service-owned defaults so a fresh database can serve `GET /api/admin/scheduling-rules` immediately.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Web MVC, Spring Security method authorization, Spring Data JPA, Bean Validation, Jackson `ObjectMapper`, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/repository/SchedulingRuleRepository.java`: Spring Data repository for `SchedulingRule` lookup by stable code.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingHardRulesRequest.java`: hard rule nested request/response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingQuotaRulesRequest.java`: quota nested request/response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingSoftWeightsRequest.java`: soft weight nested request/response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulingRulesResponse.java`: grouped API response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateSchedulingRulesRequest.java`: grouped update request DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java`: default rule initialization, grouped read, grouped update, validation, JSON serialization/parsing.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleController.java`: `GET` and `PUT` endpoints under `/api/admin/scheduling-rules` with manager/admin authorization.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`: add `@MockitoBean SchedulingRuleRepository` because this smoke test disables DataSource/JPA auto-configuration.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleServiceTest.java`: unit tests for defaults, update, validation, and corrupted stored JSON.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleControllerTest.java`: web/security/validation tests.
- Modify `docs/project_plan.md`: mark scheduling rule API items complete after full verification passes.

## Task 1: Repository and DTO Foundations

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/SchedulingRuleRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingHardRulesRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingQuotaRulesRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingSoftWeightsRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulingRulesResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateSchedulingRulesRequest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleServiceTest.java`

- [ ] **Step 1: Write the failing DTO/repository compile test**

Create `AdminSchedulingRuleServiceTest.java` with a minimal test that references the repository and DTO constructors before they exist:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
import scheduling.scheduling.admin.dto.UpdateSchedulingRulesRequest;
import scheduling.scheduling.repository.SchedulingRuleRepository;

class AdminSchedulingRuleServiceTest {

    private final SchedulingRuleRepository schedulingRuleRepository = mock(SchedulingRuleRepository.class);

    @Test
    void updateRequestGroupsRulesQuotasAndWeights() {
        UpdateSchedulingRulesRequest request = validRequest();

        assertThat(request.hardRules().minRestHoursBetweenShifts()).isEqualTo(12);
        assertThat(request.quotas().overtimeShiftsPerWeek()).isEqualTo(1);
        assertThat(request.softWeights().fairness()).isEqualByComparingTo("10.00");
        assertThat(schedulingRuleRepository).isNotNull();
    }

    private static UpdateSchedulingRulesRequest validRequest() {
        return new UpdateSchedulingRulesRequest(
                new SchedulingHardRulesRequest(12, 12, 6),
                new SchedulingQuotaRulesRequest(1, 2, 6),
                new SchedulingSoftWeightsRequest(
                        new BigDecimal("10.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("10.00")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleServiceTest"
```

Expected: compilation fails because `SchedulingRuleRepository` and DTO classes do not exist.

- [ ] **Step 3: Add repository**

Create `SchedulingRuleRepository.java`:

```java
package scheduling.scheduling.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.SchedulingRule;

public interface SchedulingRuleRepository extends JpaRepository<SchedulingRule, Long> {

    Optional<SchedulingRule> findByRuleCode(String ruleCode);

    List<SchedulingRule> findByRuleCodeIn(Collection<String> ruleCodes);
}
```

- [ ] **Step 4: Add nested DTO records with Bean Validation**

Create `SchedulingHardRulesRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SchedulingHardRulesRequest(
        @NotNull @Min(0) @Max(24) Integer minRestHoursBetweenShifts,
        @NotNull @Min(1) @Max(24) Integer maxWorkHoursPerDay,
        @NotNull @Min(0) @Max(14) Integer maxShiftsPerWeek) {
}
```

Create `SchedulingQuotaRulesRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SchedulingQuotaRulesRequest(
        @NotNull @Min(0) @Max(14) Integer overtimeShiftsPerWeek,
        @NotNull @Min(0) @Max(14) Integer bathingShiftsPerWeek,
        @NotNull @Min(1) @Max(31) Integer onCallEveryNDays) {
}
```

Create `SchedulingSoftWeightsRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SchedulingSoftWeightsRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal fairness,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal preference,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal weekend,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal heavyShift,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal workHourBalance) {
}
```

- [ ] **Step 5: Add grouped request/response DTO records**

Create `UpdateSchedulingRulesRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateSchedulingRulesRequest(
        @Valid @NotNull SchedulingHardRulesRequest hardRules,
        @Valid @NotNull SchedulingQuotaRulesRequest quotas,
        @Valid @NotNull SchedulingSoftWeightsRequest softWeights) {
}
```

Create `AdminSchedulingRulesResponse.java`:

```java
package scheduling.scheduling.admin.dto;

public record AdminSchedulingRulesResponse(
        SchedulingHardRulesRequest hardRules,
        SchedulingQuotaRulesRequest quotas,
        SchedulingSoftWeightsRequest softWeights) {
}
```

- [ ] **Step 6: Run the targeted test to verify it passes**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleServiceTest"
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit Task 1**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/SchedulingRuleRepository.java backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingHardRulesRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingQuotaRulesRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/SchedulingSoftWeightsRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulingRulesResponse.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateSchedulingRulesRequest.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleServiceTest.java
git commit -m "feat: add scheduling rule API foundations"
```

## Task 2: Service Defaults, Mapping, and Validation

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleServiceTest.java`

- [ ] **Step 1: Replace service test with failing behavior tests**

Replace `AdminSchedulingRuleServiceTest.java` with tests for defaults, persisted rows, update, validation, and malformed stored JSON:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
import scheduling.scheduling.admin.dto.UpdateSchedulingRulesRequest;
import scheduling.scheduling.entity.SchedulingRule;
import scheduling.scheduling.repository.SchedulingRuleRepository;

class AdminSchedulingRuleServiceTest {

    private final SchedulingRuleRepository schedulingRuleRepository = mock(SchedulingRuleRepository.class);
    private final AdminSchedulingRuleService service = new AdminSchedulingRuleService(schedulingRuleRepository);

    @Test
    void getRulesCreatesMissingDefaults() {
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(List.of());
        ArgumentCaptor<List<SchedulingRule>> rulesCaptor = ArgumentCaptor.captor();
        when(schedulingRuleRepository.saveAll(rulesCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminSchedulingRulesResponse response = service.getRules();

        assertThat(response.hardRules().minRestHoursBetweenShifts()).isEqualTo(12);
        assertThat(response.hardRules().maxWorkHoursPerDay()).isEqualTo(12);
        assertThat(response.hardRules().maxShiftsPerWeek()).isEqualTo(6);
        assertThat(response.quotas().overtimeShiftsPerWeek()).isEqualTo(1);
        assertThat(response.quotas().bathingShiftsPerWeek()).isEqualTo(2);
        assertThat(response.quotas().onCallEveryNDays()).isEqualTo(6);
        assertThat(response.softWeights().fairness()).isEqualByComparingTo("10.00");
        assertThat(rulesCaptor.getValue()).hasSize(11);
    }

    @Test
    void getRulesReusesExistingRowsWithoutDuplicatingDefaults() {
        List<SchedulingRule> existing = defaultRows();
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(existing);

        AdminSchedulingRulesResponse response = service.getRules();

        assertThat(response.softWeights().heavyShift()).isEqualByComparingTo("8.00");
        verify(schedulingRuleRepository).findByRuleCodeIn(anyCollection());
    }

    @Test
    void updateRulesPersistsAllValues() {
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(defaultRows());
        ArgumentCaptor<List<SchedulingRule>> rulesCaptor = ArgumentCaptor.captor();
        when(schedulingRuleRepository.saveAll(rulesCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminSchedulingRulesResponse response = service.updateRules(new UpdateSchedulingRulesRequest(
                new SchedulingHardRulesRequest(10, 12, 5),
                new SchedulingQuotaRulesRequest(1, 2, 7),
                new SchedulingSoftWeightsRequest(
                        new BigDecimal("11.00"),
                        new BigDecimal("6.00"),
                        new BigDecimal("4.00"),
                        new BigDecimal("9.00"),
                        new BigDecimal("12.00"))));

        assertThat(response.hardRules().minRestHoursBetweenShifts()).isEqualTo(10);
        assertThat(response.quotas().onCallEveryNDays()).isEqualTo(7);
        assertThat(response.softWeights().workHourBalance()).isEqualByComparingTo("12.00");
        assertThat(rulesCaptor.getValue()).hasSize(11);
        assertThat(rulesCaptor.getValue())
                .filteredOn(rule -> rule.getRuleCode().equals("MIN_REST_HOURS_BETWEEN_SHIFTS"))
                .singleElement()
                .satisfies(rule -> assertThat(rule.getRuleValueJson()).isEqualTo("{\"value\":10}"));
    }

    @Test
    void updateRulesRejectsImpossibleRestAndWorkDayCombination() {
        UpdateSchedulingRulesRequest invalid = new UpdateSchedulingRulesRequest(
                new SchedulingHardRulesRequest(13, 12, 6),
                new SchedulingQuotaRulesRequest(1, 2, 6),
                defaultWeights());

        assertThatThrownBy(() -> service.updateRules(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid scheduling rules request")
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getRulesFailsFastForMalformedStoredJson() {
        SchedulingRule broken = hardRule("MIN_REST_HOURS_BETWEEN_SHIFTS", "Minimum rest hours between shifts", "not-json");
        List<SchedulingRule> rows = new ArrayList<>(defaultRows());
        rows.removeIf(rule -> rule.getRuleCode().equals("MIN_REST_HOURS_BETWEEN_SHIFTS"));
        rows.add(broken);
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(rows);

        assertThatThrownBy(service::getRules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid scheduling rule JSON");
    }

    private static List<SchedulingRule> defaultRows() {
        return List.of(
                hardRule("MIN_REST_HOURS_BETWEEN_SHIFTS", "Minimum rest hours between shifts", "{\"value\":12}"),
                hardRule("MAX_WORK_HOURS_PER_DAY", "Maximum work hours per day", "{\"value\":12}"),
                hardRule("MAX_SHIFTS_PER_WEEK", "Maximum shifts per week", "{\"value\":6}"),
                hardRule("OVERTIME_SHIFTS_PER_WEEK", "Overtime shifts per week", "{\"value\":1}"),
                hardRule("BATHING_SHIFTS_PER_WEEK", "Bathing shifts per week", "{\"value\":2}"),
                hardRule("ON_CALL_EVERY_N_DAYS", "On-call every N days", "{\"value\":6}"),
                softRule("FAIRNESS_WEIGHT", "Fairness weight", "10.00"),
                softRule("PREFERENCE_WEIGHT", "Preference weight", "5.00"),
                softRule("WEEKEND_WEIGHT", "Weekend weight", "5.00"),
                softRule("HEAVY_SHIFT_WEIGHT", "Heavy shift weight", "8.00"),
                softRule("WORK_HOUR_BALANCE_WEIGHT", "Work hour balance weight", "10.00"));
    }

    private static SchedulingSoftWeightsRequest defaultWeights() {
        return new SchedulingSoftWeightsRequest(
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                new BigDecimal("8.00"),
                new BigDecimal("10.00"));
    }

    private static SchedulingRule hardRule(String code, String name, String json) {
        return SchedulingRule.builder()
                .ruleCode(code)
                .ruleName(name)
                .ruleType("HARD")
                .ruleValueJson(json)
                .active(true)
                .build();
    }

    private static SchedulingRule softRule(String code, String name, String weight) {
        return SchedulingRule.builder()
                .ruleCode(code)
                .ruleName(name)
                .ruleType("SOFT")
                .ruleValueJson("{\"enabled\":true}")
                .weight(new BigDecimal(weight))
                .active(true)
                .build();
    }
}
```

- [ ] **Step 2: Run service tests to verify they fail**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleServiceTest"
```

Expected: compilation fails because `AdminSchedulingRuleService` does not exist.

- [ ] **Step 3: Implement `AdminSchedulingRuleService`**

Create `AdminSchedulingRuleService.java`:

```java
package scheduling.scheduling.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
import scheduling.scheduling.admin.dto.UpdateSchedulingRulesRequest;
import scheduling.scheduling.entity.SchedulingRule;
import scheduling.scheduling.repository.SchedulingRuleRepository;

@Service
public class AdminSchedulingRuleService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HARD = "HARD";
    private static final String SOFT = "SOFT";
    private static final String ENABLED_JSON = "{\"enabled\":true}";

    private final SchedulingRuleRepository schedulingRuleRepository;

    public AdminSchedulingRuleService(SchedulingRuleRepository schedulingRuleRepository) {
        this.schedulingRuleRepository = schedulingRuleRepository;
    }

    @Transactional
    public AdminSchedulingRulesResponse getRules() {
        return toResponse(loadOrCreateRules());
    }

    @Transactional
    public AdminSchedulingRulesResponse updateRules(UpdateSchedulingRulesRequest request) {
        validate(request);
        Map<String, SchedulingRule> rules = loadOrCreateRules().stream()
                .collect(Collectors.toMap(SchedulingRule::getRuleCode, Function.identity()));

        updateHardRule(rules, "MIN_REST_HOURS_BETWEEN_SHIFTS", request.hardRules().minRestHoursBetweenShifts());
        updateHardRule(rules, "MAX_WORK_HOURS_PER_DAY", request.hardRules().maxWorkHoursPerDay());
        updateHardRule(rules, "MAX_SHIFTS_PER_WEEK", request.hardRules().maxShiftsPerWeek());
        updateHardRule(rules, "OVERTIME_SHIFTS_PER_WEEK", request.quotas().overtimeShiftsPerWeek());
        updateHardRule(rules, "BATHING_SHIFTS_PER_WEEK", request.quotas().bathingShiftsPerWeek());
        updateHardRule(rules, "ON_CALL_EVERY_N_DAYS", request.quotas().onCallEveryNDays());
        updateSoftRule(rules, "FAIRNESS_WEIGHT", request.softWeights().fairness());
        updateSoftRule(rules, "PREFERENCE_WEIGHT", request.softWeights().preference());
        updateSoftRule(rules, "WEEKEND_WEIGHT", request.softWeights().weekend());
        updateSoftRule(rules, "HEAVY_SHIFT_WEIGHT", request.softWeights().heavyShift());
        updateSoftRule(rules, "WORK_HOUR_BALANCE_WEIGHT", request.softWeights().workHourBalance());

        List<SchedulingRule> saved = schedulingRuleRepository.saveAll(new ArrayList<>(rules.values()));
        return toResponse(saved);
    }

    private List<SchedulingRule> loadOrCreateRules() {
        Map<String, RuleDefinition> definitions = definitions();
        Map<String, SchedulingRule> existing = schedulingRuleRepository.findByRuleCodeIn(definitions.keySet()).stream()
                .collect(Collectors.toMap(SchedulingRule::getRuleCode, Function.identity()));
        List<SchedulingRule> missing = definitions.entrySet().stream()
                .filter(entry -> !existing.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().newRule(entry.getKey()))
                .toList();
        if (!missing.isEmpty()) {
            schedulingRuleRepository.saveAll(missing).forEach(rule -> existing.put(rule.getRuleCode(), rule));
        }
        return definitions.keySet().stream().map(existing::get).toList();
    }

    private AdminSchedulingRulesResponse toResponse(List<SchedulingRule> rows) {
        Map<String, SchedulingRule> rules = rows.stream()
                .collect(Collectors.toMap(SchedulingRule::getRuleCode, Function.identity()));
        return new AdminSchedulingRulesResponse(
                new SchedulingHardRulesRequest(
                        readValue(rules, "MIN_REST_HOURS_BETWEEN_SHIFTS"),
                        readValue(rules, "MAX_WORK_HOURS_PER_DAY"),
                        readValue(rules, "MAX_SHIFTS_PER_WEEK")),
                new SchedulingQuotaRulesRequest(
                        readValue(rules, "OVERTIME_SHIFTS_PER_WEEK"),
                        readValue(rules, "BATHING_SHIFTS_PER_WEEK"),
                        readValue(rules, "ON_CALL_EVERY_N_DAYS")),
                new SchedulingSoftWeightsRequest(
                        rules.get("FAIRNESS_WEIGHT").getWeight(),
                        rules.get("PREFERENCE_WEIGHT").getWeight(),
                        rules.get("WEEKEND_WEIGHT").getWeight(),
                        rules.get("HEAVY_SHIFT_WEIGHT").getWeight(),
                        rules.get("WORK_HOUR_BALANCE_WEIGHT").getWeight()));
    }

    private void validate(UpdateSchedulingRulesRequest request) {
        if (request.hardRules().minRestHoursBetweenShifts() + request.hardRules().maxWorkHoursPerDay() > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scheduling rules request");
        }
    }

    private void updateHardRule(Map<String, SchedulingRule> rules, String code, Integer value) {
        SchedulingRule rule = rules.get(code);
        rule.setRuleType(HARD);
        rule.setRuleValueJson(valueJson(value));
        rule.setWeight(null);
        rule.setActive(true);
    }

    private void updateSoftRule(Map<String, SchedulingRule> rules, String code, BigDecimal weight) {
        SchedulingRule rule = rules.get(code);
        rule.setRuleType(SOFT);
        rule.setRuleValueJson(ENABLED_JSON);
        rule.setWeight(weight);
        rule.setActive(true);
    }

    private Integer readValue(Map<String, SchedulingRule> rules, String code) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rules.get(code).getRuleValueJson());
            return root.path("value").asInt();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid scheduling rule JSON for " + code, exception);
        }
    }

    private static String valueJson(Integer value) {
        return "{\"value\":" + value + "}";
    }

    private static Map<String, RuleDefinition> definitions() {
        Map<String, RuleDefinition> definitions = new LinkedHashMap<>();
        definitions.put("MIN_REST_HOURS_BETWEEN_SHIFTS", RuleDefinition.hard("Minimum rest hours between shifts", 12));
        definitions.put("MAX_WORK_HOURS_PER_DAY", RuleDefinition.hard("Maximum work hours per day", 12));
        definitions.put("MAX_SHIFTS_PER_WEEK", RuleDefinition.hard("Maximum shifts per week", 6));
        definitions.put("OVERTIME_SHIFTS_PER_WEEK", RuleDefinition.hard("Overtime shifts per week", 1));
        definitions.put("BATHING_SHIFTS_PER_WEEK", RuleDefinition.hard("Bathing shifts per week", 2));
        definitions.put("ON_CALL_EVERY_N_DAYS", RuleDefinition.hard("On-call every N days", 6));
        definitions.put("FAIRNESS_WEIGHT", RuleDefinition.soft("Fairness weight", new BigDecimal("10.00")));
        definitions.put("PREFERENCE_WEIGHT", RuleDefinition.soft("Preference weight", new BigDecimal("5.00")));
        definitions.put("WEEKEND_WEIGHT", RuleDefinition.soft("Weekend weight", new BigDecimal("5.00")));
        definitions.put("HEAVY_SHIFT_WEIGHT", RuleDefinition.soft("Heavy shift weight", new BigDecimal("8.00")));
        definitions.put("WORK_HOUR_BALANCE_WEIGHT", RuleDefinition.soft("Work hour balance weight", new BigDecimal("10.00")));
        return definitions;
    }

    private record RuleDefinition(String name, String type, Integer value, BigDecimal weight) {

        static RuleDefinition hard(String name, Integer value) {
            return new RuleDefinition(name, HARD, value, null);
        }

        static RuleDefinition soft(String name, BigDecimal weight) {
            return new RuleDefinition(name, SOFT, null, weight);
        }

        SchedulingRule newRule(String code) {
            return SchedulingRule.builder()
                    .ruleCode(code)
                    .ruleName(name)
                    .ruleType(type)
                    .ruleValueJson(value == null ? ENABLED_JSON : valueJson(value))
                    .weight(weight)
                    .active(true)
                    .build();
        }
    }
}
```

- [ ] **Step 4: Run service tests to verify green**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleServiceTest"
```

Expected: all service tests pass.

- [ ] **Step 5: Commit Task 2**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleServiceTest.java
git commit -m "feat: manage admin scheduling rules"
```

## Task 3: Controller and Security

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleController.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `AdminSchedulingRuleControllerTest.java`:

```java
package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminSchedulingRuleController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSchedulingRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSchedulingRuleService adminSchedulingRuleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerGetsSchedulingRules() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);
        when(adminSchedulingRuleService.getRules()).thenReturn(response());

        mockMvc.perform(get("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardRules.minRestHoursBetweenShifts").value(12))
                .andExpect(jsonPath("$.quotas.overtimeShiftsPerWeek").value(1))
                .andExpect(jsonPath("$.softWeights.fairness").value(10.00));
    }

    @Test
    void adminUpdatesSchedulingRules() throws Exception {
        CurrentAccountPrincipal admin = new CurrentAccountPrincipal(1L, "admin01", AccountRole.ADMIN);
        givenToken("admin-token", admin);
        when(adminSchedulingRuleService.updateRules(any())).thenReturn(response());

        mockMvc.perform(put("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardRules.maxWorkHoursPerDay").value(12));

        verify(adminSchedulingRuleService).updateRules(any());
    }

    @Test
    void employeeCannotGetSchedulingRules() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(get("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulingRuleService);
    }

    @Test
    void invalidUpdateBodyReturnsBadRequestWithoutDelegating() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(put("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hardRules\":{\"minRestHoursBetweenShifts\":25}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminSchedulingRuleService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static AdminSchedulingRulesResponse response() {
        return new AdminSchedulingRulesResponse(
                new SchedulingHardRulesRequest(12, 12, 6),
                new SchedulingQuotaRulesRequest(1, 2, 6),
                new SchedulingSoftWeightsRequest(
                        new BigDecimal("10.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("10.00")));
    }

    private static String validJson() {
        return """
                {
                  "hardRules": {
                    "minRestHoursBetweenShifts": 12,
                    "maxWorkHoursPerDay": 12,
                    "maxShiftsPerWeek": 6
                  },
                  "quotas": {
                    "overtimeShiftsPerWeek": 1,
                    "bathingShiftsPerWeek": 2,
                    "onCallEveryNDays": 6
                  },
                  "softWeights": {
                    "fairness": 10.00,
                    "preference": 5.00,
                    "weekend": 5.00,
                    "heavyShift": 8.00,
                    "workHourBalance": 10.00
                  }
                }
                """;
    }
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleControllerTest"
```

Expected: compilation fails because `AdminSchedulingRuleController` does not exist.

- [ ] **Step 3: Add controller**

Create `AdminSchedulingRuleController.java`:

```java
package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.UpdateSchedulingRulesRequest;

@RestController
@RequestMapping("/api/admin/scheduling-rules")
public class AdminSchedulingRuleController {

    private final AdminSchedulingRuleService adminSchedulingRuleService;

    public AdminSchedulingRuleController(AdminSchedulingRuleService adminSchedulingRuleService) {
        this.adminSchedulingRuleService = adminSchedulingRuleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminSchedulingRulesResponse> getRules() {
        return ResponseEntity.ok(adminSchedulingRuleService.getRules());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminSchedulingRulesResponse> updateRules(@Valid @RequestBody UpdateSchedulingRulesRequest request) {
        return ResponseEntity.ok(adminSchedulingRuleService.updateRules(request));
    }
}
```

- [ ] **Step 4: Run controller tests to verify green**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulingRuleControllerTest"
```

Expected: all controller tests pass.

- [ ] **Step 5: Commit Task 3**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleController.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulingRuleControllerTest.java
git commit -m "feat: expose admin scheduling rule APIs"
```

## Task 4: Full Context Smoke Test and Project Plan Update

**Files:**
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Run smoke test and capture current failure if any**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=SchedulingApplicationTests"
```

Expected before the fix: if JPA is excluded, Spring context fails because `SchedulingRuleRepository` has no bean.

- [ ] **Step 2: Add repository mock to smoke test**

Modify `SchedulingApplicationTests.java` imports:

```java
import scheduling.scheduling.repository.SchedulingRuleRepository;
```

Add this field after existing repository mocks:

```java
@MockitoBean
private SchedulingRuleRepository schedulingRuleRepository;
```

- [ ] **Step 3: Run smoke test to verify green**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=SchedulingApplicationTests"
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 4: Update project plan only after tests pass**

Modify `docs/project_plan.md`:

- Mark `API quan ly scheduling rules` complete.
- Mark `API cau hinh dinh muc ca/tuan` complete.
- Mark `API cau hinh trong so soft constraints` complete.
- Update current branch to `phase3/scheduling-rule-apis`.

- [ ] **Step 5: Commit Task 4**

Run from repo root:

```powershell
git add backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java docs/project_plan.md
git commit -m "docs: update scheduling rule api progress"
```

## Task 5: Verification, Review, and Push Preparation

**Files:**
- Verify all files touched in Tasks 1-4.

- [ ] **Step 1: Run full backend test suite**

Run from `backend`:

```powershell
.\mvnw.cmd clean test
```

Expected: build success with all tests passing.

- [ ] **Step 2: Inspect git diff from `origin/main`**

Run from repo root:

```powershell
git status --short --branch
git diff --stat origin/main..HEAD
git log --oneline origin/main..HEAD
```

Expected: branch contains only scheduling-rule spec/plan/API/test/docs commits.

- [ ] **Step 3: Request code review**

Use `superpowers:requesting-code-review` with base `origin/main` and current `HEAD`. Ask reviewer to check:

- rule defaults match project requirements,
- JSON persistence is safe and deterministic,
- validation matches spec,
- manager/admin security is enforced,
- tests cover service/controller/context behavior.

- [ ] **Step 4: Address review findings**

If reviewer reports Critical or Important findings, fix them with TDD:

1. Write or update a failing targeted test.
2. Run the targeted test and confirm the expected failure.
3. Implement the minimal fix.
4. Rerun targeted test.
5. Rerun `.\mvnw.cmd clean test` from `backend`.
6. Commit the fix.

- [ ] **Step 5: Push only after verification passes**

Run from repo root:

```powershell
git push -u origin phase3/scheduling-rule-apis
```

If GitHub CLI is available, create a PR:

```powershell
gh pr create --base main --head phase3/scheduling-rule-apis --title "feat: add admin scheduling rule APIs" --body "## Summary`n- Add global admin scheduling rule read/update APIs.`n- Persist hard/quota rules and soft weights using existing scheduling_rules rows.`n- Add service/controller/context tests and update Phase 3 progress.`n`n## Test Plan`n- [x] .\\mvnw.cmd clean test"
```

If `gh` is not available, report the PR URL:

```text
https://github.com/WestTand/scheduling_platform/pull/new/phase3/scheduling-rule-apis
```

## Self-Review

- Spec coverage: Tasks cover repository, DTOs, global defaults, GET/PUT APIs, manager/admin role protection, validation, JSON persistence, context smoke test, roadmap update, and final review/push. The future AI-service payload and OR-Tools implementation are intentionally out of scope and deferred to Phase 6.
- Placeholder scan: No placeholder tokens, open-ended implementation placeholders, or manual code-fix notes remain.
- Type consistency: DTO names, method names, endpoint paths, rule code strings, and validation bounds match the spec. The response intentionally contains only `hardRules`, `quotas`, and `softWeights` because `BaseEntity` currently has no timestamp fields.

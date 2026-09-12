package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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
        verify(schedulingRuleRepository, never()).saveAll(anyCollection());
    }

    @Test
    void getRulesRecoversWhenMissingDefaultInsertLosesRace() {
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(List.of(), defaultRows());
        when(schedulingRuleRepository.saveAll(anyCollection()))
                .thenThrow(new DataIntegrityViolationException("duplicate rule code"));

        AdminSchedulingRulesResponse response = service.getRules();

        assertThat(response.hardRules().minRestHoursBetweenShifts()).isEqualTo(12);
        assertThat(response.softWeights().fairness()).isEqualByComparingTo("10.00");
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

    @Test
    void getRulesFailsFastForHardRuleJsonMissingNumericValue() {
        SchedulingRule missingValue = hardRule("MIN_REST_HOURS_BETWEEN_SHIFTS", "Minimum rest hours between shifts", "{}");
        List<SchedulingRule> rows = new ArrayList<>(defaultRows());
        rows.removeIf(rule -> rule.getRuleCode().equals("MIN_REST_HOURS_BETWEEN_SHIFTS"));
        rows.add(missingValue);
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(rows);

        assertThatThrownBy(service::getRules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid scheduling rule JSON");
    }

    @Test
    void getRulesFailsFastForHardRuleJsonWithNonNumericValue() {
        SchedulingRule nonNumeric = hardRule(
                "MIN_REST_HOURS_BETWEEN_SHIFTS", "Minimum rest hours between shifts", "{\"value\":\"abc\"}");
        List<SchedulingRule> rows = new ArrayList<>(defaultRows());
        rows.removeIf(rule -> rule.getRuleCode().equals("MIN_REST_HOURS_BETWEEN_SHIFTS"));
        rows.add(nonNumeric);
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(rows);

        assertThatThrownBy(service::getRules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid scheduling rule JSON");
    }

    @Test
    void getRulesFailsFastForHardRuleJsonWithoutValueField() {
        SchedulingRule enabledOnly = hardRule(
                "MIN_REST_HOURS_BETWEEN_SHIFTS", "Minimum rest hours between shifts", "{\"enabled\":true}");
        List<SchedulingRule> rows = new ArrayList<>(defaultRows());
        rows.removeIf(rule -> rule.getRuleCode().equals("MIN_REST_HOURS_BETWEEN_SHIFTS"));
        rows.add(enabledOnly);
        when(schedulingRuleRepository.findByRuleCodeIn(anyCollection())).thenReturn(rows);

        assertThatThrownBy(service::getRules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid scheduling rule JSON");
    }

    @Test
    void getRulesFailsFastForMalformedStoredSoftRuleJson() {
        SchedulingRule broken = softRule("FAIRNESS_WEIGHT", "Fairness weight", "10.00");
        broken.setRuleValueJson("not-json");
        List<SchedulingRule> rows = new ArrayList<>(defaultRows());
        rows.removeIf(rule -> rule.getRuleCode().equals("FAIRNESS_WEIGHT"));
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

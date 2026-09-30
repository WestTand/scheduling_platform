package scheduling.scheduling.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
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

    public AdminSchedulingRulesResponse getRules() {
        return toResponse(loadOrCreateRules());
    }

    private synchronized List<SchedulingRule> loadOrCreateRules() {
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
        validateRuleJson(rules, "FAIRNESS_WEIGHT");
        validateRuleJson(rules, "PREFERENCE_WEIGHT");
        validateRuleJson(rules, "WEEKEND_WEIGHT");
        validateRuleJson(rules, "HEAVY_SHIFT_WEIGHT");
        validateRuleJson(rules, "WORK_HOUR_BALANCE_WEIGHT");
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

    private Integer readValue(Map<String, SchedulingRule> rules, String code) {
        JsonNode root = readRuleJson(rules, code);
        JsonNode value = root.get("value");
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalStateException("Invalid scheduling rule JSON for " + code);
        }
        return value.asInt();
    }

    private void validateRuleJson(Map<String, SchedulingRule> rules, String code) {
        readRuleJson(rules, code);
    }

    private JsonNode readRuleJson(Map<String, SchedulingRule> rules, String code) {
        try {
            return OBJECT_MAPPER.readTree(rules.get(code).getRuleValueJson());
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

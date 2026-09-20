package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import scheduling.scheduling.repository.SchedulingRuleRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class SchedulingRuleJsonbMappingTest {

    private static final String RULE_CODE = "JSONB_MAPPING_TEST";

    @Autowired
    private SchedulingRuleRepository schedulingRuleRepository;

    @AfterEach
    void tearDown() {
        schedulingRuleRepository.findByRuleCode(RULE_CODE).ifPresent(schedulingRuleRepository::delete);
    }

    @Test
    void persistsRuleValueJsonToPostgresJsonbColumn() throws Exception {
        SchedulingRule saved = schedulingRuleRepository.save(SchedulingRule.builder()
                .ruleCode(RULE_CODE)
                .ruleName("JSONB Mapping Test")
                .ruleType("HARD")
                .ruleValueJson("{\"value\":12}")
                .weight(new BigDecimal("1.00"))
                .active(true)
                .build());

        SchedulingRule reloaded = schedulingRuleRepository.findById(saved.getId()).orElseThrow();

        // Postgres jsonb normalizes whitespace, so compare parsed JSON semantically.
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readTree(reloaded.getRuleValueJson()))
                .isEqualTo(mapper.readTree("{\"value\":12}"));
    }
}

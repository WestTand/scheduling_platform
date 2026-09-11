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

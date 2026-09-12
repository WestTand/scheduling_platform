package scheduling.scheduling.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateSchedulingRulesRequest(
        @Valid @NotNull SchedulingHardRulesRequest hardRules,
        @Valid @NotNull SchedulingQuotaRulesRequest quotas,
        @Valid @NotNull SchedulingSoftWeightsRequest softWeights) {
}

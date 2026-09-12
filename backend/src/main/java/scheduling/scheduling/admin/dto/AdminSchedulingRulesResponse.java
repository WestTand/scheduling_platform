package scheduling.scheduling.admin.dto;

public record AdminSchedulingRulesResponse(
        SchedulingHardRulesRequest hardRules,
        SchedulingQuotaRulesRequest quotas,
        SchedulingSoftWeightsRequest softWeights) {
}

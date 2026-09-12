package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SchedulingQuotaRulesRequest(
        @NotNull @Min(0) @Max(14) Integer overtimeShiftsPerWeek,
        @NotNull @Min(0) @Max(14) Integer bathingShiftsPerWeek,
        @NotNull @Min(1) @Max(31) Integer onCallEveryNDays) {
}

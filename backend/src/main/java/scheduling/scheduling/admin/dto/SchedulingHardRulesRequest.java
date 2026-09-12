package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SchedulingHardRulesRequest(
        @NotNull @Min(0) @Max(24) Integer minRestHoursBetweenShifts,
        @NotNull @Min(1) @Max(24) Integer maxWorkHoursPerDay,
        @NotNull @Min(0) @Max(14) Integer maxShiftsPerWeek) {
}

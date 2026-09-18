package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

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
        @NotBlank @Size(max = 30) String shiftCode,
        String applicableWeekdays,
        Boolean appliesOnHolidays,
        BigDecimal penaltyWeightMultiplier) {
}

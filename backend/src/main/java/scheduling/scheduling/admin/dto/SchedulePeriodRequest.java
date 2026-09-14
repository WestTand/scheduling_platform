package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SchedulePeriodRequest(
        @NotBlank String periodName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) Integer versionNo) {
}

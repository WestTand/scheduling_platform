package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SchedulePeriodRequest(
        @NotBlank @Size(max = 100) String periodName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) Integer versionNo) {
}

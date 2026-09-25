package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record GenerateScheduleRequest(
        @NotBlank String periodName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Positive Integer versionNo,
        @NotEmpty List<Long> employeeIds,
        @NotEmpty List<Long> shiftTypeIds) {
}

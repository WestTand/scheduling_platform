package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateWorkShiftRequest(
        @NotNull Long shiftTypeId,
        @NotNull LocalDate workDate,
        @Min(1) Integer requiredStaff,
        String note) {
}

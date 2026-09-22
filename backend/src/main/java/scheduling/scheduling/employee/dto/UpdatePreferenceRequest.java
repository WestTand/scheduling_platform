package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePreferenceRequest(
        @NotNull LocalDate preferenceDate,
        @NotBlank @Size(max = 20) String preferenceType,
        Long shiftTypeId,
        @Size(max = 50) String shiftCode,
        @NotNull @Min(1) Integer priority,
        @Size(max = 1000) String note) {
}

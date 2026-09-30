package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RespondSwapRequestRequest(@NotNull Boolean accept, @Positive Long targetAssignmentId) {
}

package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;

public record RespondSwapRequestRequest(@NotNull Boolean accept, Long targetAssignmentId) {
}

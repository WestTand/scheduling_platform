package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSwapRequestRequest(
        @NotNull Long requesterAssignmentId,
        @NotNull Long targetEmployeeId,
        Long targetAssignmentId,
        @Size(max = 1000) String reason) {
}

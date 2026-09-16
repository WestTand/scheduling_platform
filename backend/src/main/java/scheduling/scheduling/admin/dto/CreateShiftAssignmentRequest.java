package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotNull;

public record CreateShiftAssignmentRequest(@NotNull Long employeeId) {
}

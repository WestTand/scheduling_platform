package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShiftAssignmentRequest(@NotNull Long employeeId) {
}

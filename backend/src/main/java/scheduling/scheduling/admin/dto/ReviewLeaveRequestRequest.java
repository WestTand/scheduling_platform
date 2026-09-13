package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Size;

public record ReviewLeaveRequestRequest(@Size(max = 1000) String reviewNote) {
}

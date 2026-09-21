package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeAccountRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 50) String username) {

    public CreateEmployeeAccountRequest {
        if (username != null) {
            username = username.trim();
        }
    }
}

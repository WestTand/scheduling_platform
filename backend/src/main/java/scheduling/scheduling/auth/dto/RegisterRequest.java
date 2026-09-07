package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String employeeCode,
        @NotBlank String fullName,
        String email,
        String phone) {
}

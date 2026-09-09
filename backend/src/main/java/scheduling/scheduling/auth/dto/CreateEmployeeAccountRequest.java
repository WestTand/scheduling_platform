package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmployeeAccountRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone) {
}

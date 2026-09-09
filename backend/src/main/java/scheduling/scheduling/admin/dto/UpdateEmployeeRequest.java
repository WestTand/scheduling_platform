package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
}

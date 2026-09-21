package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {

    public CreateEmployeeRequest {
        employeeCode = trimNullable(employeeCode);
        fullName = trimNullable(fullName);
        email = trimToNull(email);
        phone = trimToNull(phone);
        status = trimNullable(status);
    }

    private static String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trimNullable(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }
}

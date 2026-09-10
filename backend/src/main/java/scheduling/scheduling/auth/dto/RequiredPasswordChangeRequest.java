package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredPasswordChangeRequest(
        @NotNull Long accountId,
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}

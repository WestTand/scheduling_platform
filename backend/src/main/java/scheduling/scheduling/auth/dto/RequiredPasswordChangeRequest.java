package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredPasswordChangeRequest(
        @NotNull Long accountId,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}

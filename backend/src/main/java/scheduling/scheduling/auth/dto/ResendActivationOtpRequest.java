package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendActivationOtpRequest(@NotBlank String username, @NotBlank String temporaryPassword) {
}

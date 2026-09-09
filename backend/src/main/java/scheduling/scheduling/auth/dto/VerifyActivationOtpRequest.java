package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyActivationOtpRequest(
        @NotNull Long accountId,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}

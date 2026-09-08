package scheduling.scheduling.auth;

import scheduling.scheduling.auth.dto.AuthResponse;

public record LoginResult(AuthResponse response, String refreshToken) {
}

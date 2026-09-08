package scheduling.scheduling.auth;

import scheduling.scheduling.entity.RefreshToken;

public record RefreshTokenResult(String rawToken, RefreshToken entity) {
}

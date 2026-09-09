package scheduling.scheduling.auth;

import scheduling.scheduling.auth.dto.AuthResponse;
import scheduling.scheduling.auth.dto.LoginStateResponse;

public record LoginResult(AuthResponse response, String refreshToken, LoginStateResponse stateResponse) {

    public LoginResult(AuthResponse response, String refreshToken) {
        this(response, refreshToken, null);
    }

    public static LoginResult state(LoginStateResponse response) {
        return new LoginResult(null, null, response);
    }
}

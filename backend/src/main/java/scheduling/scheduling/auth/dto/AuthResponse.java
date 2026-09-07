package scheduling.scheduling.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, AccountResponse account) {
}

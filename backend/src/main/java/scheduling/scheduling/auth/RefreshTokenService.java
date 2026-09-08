package scheduling.scheduling.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.RefreshToken;
import scheduling.scheduling.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.auth.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this(refreshTokenRepository, Duration.ofSeconds(refreshTokenTtlSeconds), Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Duration refreshTokenTtl, Clock clock) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public RefreshTokenResult create(Account account) {
        Objects.requireNonNull(account, "account");

        String rawToken = generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now().plus(refreshTokenTtl))
                .build();

        return new RefreshTokenResult(rawToken, refreshTokenRepository.save(refreshToken));
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefreshToken();
        }

        return requireValid(refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(RefreshTokenService::invalidRefreshToken));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findLockedByTokenHash(hashToken(rawToken))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> refreshToken.setRevokedAt(now()));
    }

    @Transactional
    public RefreshTokenResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefreshToken();
        }

        RefreshToken oldToken = requireValid(refreshTokenRepository.findLockedByTokenHash(hashToken(rawToken))
                .orElseThrow(RefreshTokenService::invalidRefreshToken));
        oldToken.setRevokedAt(now());
        return create(oldToken.getAccount());
    }

    private RefreshToken requireValid(RefreshToken refreshToken) {
        if (refreshToken.isRevoked() || refreshToken.isExpired(now())) {
            throw invalidRefreshToken();
        }

        return refreshToken;
    }

    static String hashTokenForTest(String rawToken) {
        return hashToken(rawToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static InvalidTokenException invalidRefreshToken() {
        return new InvalidTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
    }
}

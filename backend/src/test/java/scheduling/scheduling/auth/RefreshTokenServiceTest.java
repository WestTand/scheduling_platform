package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.Lock;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.RefreshToken;
import scheduling.scheduling.repository.RefreshTokenRepository;

class RefreshTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofDays(7);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC);

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(repository, TTL, FIXED_CLOCK);

    @Test
    void createsOpaqueRefreshTokenAndStoresOnlyHash() {
        Account account = account("employee01");
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenResult result = service.create(account);

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.entity().getTokenHash()).isNotEqualTo(result.rawToken());
        assertThat(result.entity().getAccount()).isSameAs(account);
        assertThat(result.entity().getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void rejectsRevokedToken() {
        RefreshToken token = refreshToken("raw", account("employee01"), NOW.plus(TTL));
        token.setRevokedAt(NOW.minusMinutes(1));
        when(repository.findByTokenHash(RefreshTokenService.hashTokenForTest("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.validate("raw"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void rejectsExpiredToken() {
        RefreshToken token = refreshToken("raw", account("employee01"), NOW.minusSeconds(1));
        when(repository.findByTokenHash(RefreshTokenService.hashTokenForTest("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.validate("raw"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void rotateRevokesOldTokenAndReturnsNewTokenForSameAccount() {
        Account account = account("employee01");
        RefreshToken oldToken = refreshToken("raw", account, NOW.plus(TTL));
        when(repository.findLockedByTokenHash(RefreshTokenService.hashTokenForTest("raw"))).thenReturn(Optional.of(oldToken));
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenResult result = service.rotate("raw");

        assertThat(oldToken.getRevokedAt()).isEqualTo(NOW);
        assertThat(result.rawToken()).isNotBlank().isNotEqualTo("raw");
        assertThat(result.entity().getAccount()).isSameAs(account);
        assertThat(result.entity().getTokenHash()).isNotEqualTo(oldToken.getTokenHash());

        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(savedToken.capture());
        verify(repository).findLockedByTokenHash(RefreshTokenService.hashTokenForTest("raw"));
        verify(repository, never()).findByTokenHash(RefreshTokenService.hashTokenForTest("raw"));
        assertThat(savedToken.getValue()).isSameAs(result.entity());
    }

    @Test
    void revokeUsesLockedTokenLookup() {
        RefreshToken token = refreshToken("raw", account("employee01"), NOW.plus(TTL));
        when(repository.findLockedByTokenHash(RefreshTokenService.hashTokenForTest("raw"))).thenReturn(Optional.of(token));

        service.revoke("raw");

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        verify(repository).findLockedByTokenHash(RefreshTokenService.hashTokenForTest("raw"));
        verify(repository, never()).findByTokenHash(RefreshTokenService.hashTokenForTest("raw"));
    }

    @Test
    void revokeAllForAccountMarksUnrevokedAccountTokensRevoked() {
        RefreshToken first = refreshToken("raw-1", account("employee01"), NOW.plus(TTL));
        RefreshToken second = refreshToken("raw-2", account("employee01"), NOW.plus(TTL));
        when(repository.findByAccountIdAndRevokedAtIsNull(42L)).thenReturn(List.of(first, second));

        service.revokeAllForAccount(42L);

        assertThat(first.getRevokedAt()).isEqualTo(NOW);
        assertThat(second.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void lockedLookupUsesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = RefreshTokenRepository.class.getMethod("findLockedByTokenHash", String.class);

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private static RefreshToken refreshToken(String rawToken, Account account, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .account(account)
                .tokenHash(RefreshTokenService.hashTokenForTest(rawToken))
                .expiresAt(expiresAt)
                .build();
    }

    private static Account account(String username) {
        return Account.builder()
                .username(username)
                .passwordHash("password-hash")
                .role(AccountRole.EMPLOYEE)
                .build();
    }
}

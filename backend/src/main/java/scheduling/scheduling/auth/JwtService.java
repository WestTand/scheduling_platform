package scheduling.scheduling.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import scheduling.scheduling.entity.AccountRole;

@Service
public class JwtService {

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final Clock clock;

	@Autowired
	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
		this(secret, Duration.ofSeconds(accessTokenTtlSeconds));
	}

	JwtService(String secret, Duration accessTokenTtl) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = accessTokenTtl;
		this.clock = Clock.systemUTC();
	}

	public String createAccessToken(Long accountId, String username, AccountRole role) {
		Instant issuedAt = clock.instant();
		Instant expiration = issuedAt.plus(accessTokenTtl);

		return Jwts.builder()
				.subject(username)
				.claim("accountId", accountId)
				.claim("role", role.name())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiration))
				.signWith(key)
				.compact();
	}

	public CurrentAccountPrincipal parseAccessToken(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			Number accountId = claims.get("accountId", Number.class);
			String role = claims.get("role", String.class);

			return new CurrentAccountPrincipal(accountId.longValue(), claims.getSubject(), AccountRole.valueOf(role));
		}
		catch (JwtException | IllegalArgumentException | NullPointerException ex) {
			throw new InvalidTokenException("Invalid access token");
		}
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtl.toSeconds();
	}
}

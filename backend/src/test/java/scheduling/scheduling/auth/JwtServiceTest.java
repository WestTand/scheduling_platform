package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import scheduling.scheduling.entity.AccountRole;

class JwtServiceTest {

	private static final String SECRET = "test-secret-key-with-at-least-32-characters";

	@Test
	void createsAndParsesAccessToken() {
		JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15));

		String token = jwtService.createAccessToken(10L, "employee01", AccountRole.EMPLOYEE);
		CurrentAccountPrincipal principal = jwtService.parseAccessToken(token);

		assertThat(principal.accountId()).isEqualTo(10L);
		assertThat(principal.username()).isEqualTo("employee01");
		assertThat(principal.role()).isEqualTo(AccountRole.EMPLOYEE);
	}

	@Test
	void rejectsInvalidAccessToken() {
		JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15));

		assertThatThrownBy(() -> jwtService.parseAccessToken("bad-token"))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessage("Invalid access token");
	}
}

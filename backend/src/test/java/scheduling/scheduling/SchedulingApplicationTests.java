package scheduling.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import scheduling.scheduling.repository.RefreshTokenRepository;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"app.jwt.secret=test-secret-key-with-at-least-32-characters" })
class SchedulingApplicationTests {

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private EmployeeRepository employeeRepository;

	@Test
	void contextLoads() {
	}

}

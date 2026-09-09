package scheduling.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

import scheduling.scheduling.repository.RefreshTokenRepository;
import scheduling.scheduling.repository.AccountActivationOtpRepository;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"spring.mail.host=smtp.gmail.com",
		"app.auth.default-employee-password=configured-default",
		"app.jwt.secret=test-secret-key-with-at-least-32-characters" })
class SchedulingApplicationTests {

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private EmployeeRepository employeeRepository;

	@MockitoBean
	private AccountActivationOtpRepository accountActivationOtpRepository;

	@Autowired
	private JavaMailSender mailSender;

	@Test
	void contextLoads() {
	}

	@Test
	void configuresJavaMailSender() {
		assertThat(mailSender).isNotNull();
	}

}

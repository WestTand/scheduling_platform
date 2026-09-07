package scheduling.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"app.jwt.secret=test-secret-key-with-at-least-32-characters" })
class SchedulingApplicationTests {

	@Test
	void contextLoads() {
	}

}

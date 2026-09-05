package scheduling.scheduling;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

	@GetMapping("/api/health")
	Map<String, String> health() {
		return Map.of(
				"service", "backend",
				"status", "UP");
	}
}

@Configuration
class HealthSecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/api/health").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.build();
	}
}

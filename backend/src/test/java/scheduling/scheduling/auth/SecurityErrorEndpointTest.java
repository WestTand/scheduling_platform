package scheduling.scheduling.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(controllers = SecurityErrorEndpointTest.DummyController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, ErrorMvcAutoConfiguration.class })
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173"
})
class SecurityErrorEndpointTest {

    @RestController
    static class DummyController {
        @GetMapping("/dummy")
        String dummy() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void errorEndpointIsPublicSoOriginalErrorsAreNotMaskedAsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/error"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotEqualTo(401);
    }
}

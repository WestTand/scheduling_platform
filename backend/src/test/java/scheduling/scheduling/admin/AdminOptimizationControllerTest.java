package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.OptimizeScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminOptimizationController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOptimizationService adminOptimizationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerOptimizesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminOptimizationService.optimize(
                any(CurrentAccountPrincipal.class), eq(5L), any()))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods/5/optimize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(900))
                .andExpect(jsonPath("$.aiStatus").value("SUCCESS"));

        verify(adminOptimizationService).optimize(
                any(CurrentAccountPrincipal.class), eq(5L), any());
    }

    @Test
    void employeeCannotOptimizeSchedulePeriod() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/admin/schedule-periods/5/optimize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminOptimizationService);
    }

    @Test
    void propagatesBadGatewayWhenAiTransportFails() throws Exception {
        givenToken("manager-token", manager());
        when(adminOptimizationService.optimize(
                any(CurrentAccountPrincipal.class), eq(5L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable"));

        mockMvc.perform(post("/api/admin/schedule-periods/5/optimize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void validatesSolverOptionsJson() throws Exception {
        givenToken("manager-token", manager());
        when(adminOptimizationService.optimize(
                any(CurrentAccountPrincipal.class), eq(5L), any()))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods/5/optimize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solverOptions": {
                                    "timeLimitSeconds": 10,
                                    "randomSeed": 2,
                                    "numWorkers": 4
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(900));

        ArgumentCaptor<OptimizeScheduleRequest> requestCaptor =
                ArgumentCaptor.forClass(OptimizeScheduleRequest.class);
        verify(adminOptimizationService).optimize(
                any(CurrentAccountPrincipal.class), eq(5L), requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isNotNull();
        assertThat(requestCaptor.getValue().solverOptions()).isNotNull();
        assertThat(requestCaptor.getValue().solverOptions().timeLimitSeconds()).isEqualTo(10);
        assertThat(requestCaptor.getValue().solverOptions().randomSeed()).isEqualTo(2);
        assertThat(requestCaptor.getValue().solverOptions().numWorkers()).isEqualTo(4);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static OptimizeScheduleResponse response() {
        return new OptimizeScheduleResponse(
                900L,
                5L,
                "SUCCESS",
                "OPTIMAL",
                new BigDecimal("12.5"),
                2,
                List.of(),
                null,
                LocalDateTime.of(2026, 9, 19, 10, 0, 0),
                LocalDateTime.of(2026, 9, 19, 10, 0, 30));
    }
}

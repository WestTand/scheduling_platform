package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;
import scheduling.scheduling.admin.dto.SchedulingHardRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingQuotaRulesRequest;
import scheduling.scheduling.admin.dto.SchedulingSoftWeightsRequest;
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

@WebMvcTest(AdminSchedulingRuleController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSchedulingRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSchedulingRuleService adminSchedulingRuleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerGetsSchedulingRules() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);
        when(adminSchedulingRuleService.getRules()).thenReturn(response());

        mockMvc.perform(get("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardRules.minRestHoursBetweenShifts").value(12))
                .andExpect(jsonPath("$.quotas.overtimeShiftsPerWeek").value(1))
                .andExpect(jsonPath("$.softWeights.fairness").value(10.00));
    }

    @Test
    void adminUpdatesSchedulingRules() throws Exception {
        CurrentAccountPrincipal admin = new CurrentAccountPrincipal(1L, "admin01", AccountRole.ADMIN);
        givenToken("admin-token", admin);
        when(adminSchedulingRuleService.updateRules(any())).thenReturn(response());

        mockMvc.perform(put("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardRules.maxWorkHoursPerDay").value(12));

        verify(adminSchedulingRuleService).updateRules(any());
    }

    @Test
    void employeeCannotGetSchedulingRules() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(get("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulingRuleService);
    }

    @Test
    void invalidUpdateBodyReturnsBadRequestWithoutDelegating() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(put("/api/admin/scheduling-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hardRules\":{\"minRestHoursBetweenShifts\":25}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminSchedulingRuleService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
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

    private static AdminSchedulingRulesResponse response() {
        return new AdminSchedulingRulesResponse(
                new SchedulingHardRulesRequest(12, 12, 6),
                new SchedulingQuotaRulesRequest(1, 2, 6),
                new SchedulingSoftWeightsRequest(
                        new BigDecimal("10.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("10.00")));
    }

    private static String validJson() {
        return """
                {
                  "hardRules": {
                    "minRestHoursBetweenShifts": 12,
                    "maxWorkHoursPerDay": 12,
                    "maxShiftsPerWeek": 6
                  },
                  "quotas": {
                    "overtimeShiftsPerWeek": 1,
                    "bathingShiftsPerWeek": 2,
                    "onCallEveryNDays": 6
                  },
                  "softWeights": {
                    "fairness": 10.00,
                    "preference": 5.00,
                    "weekend": 5.00,
                    "heavyShift": 8.00,
                    "workHourBalance": 10.00
                  }
                }
                """;
    }
}

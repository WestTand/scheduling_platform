package scheduling.scheduling.navigation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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

@WebMvcTest(NavigationSummaryController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
class NavigationSummaryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NavigationSummaryService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthService authService;
    @MockitoBean AccountRepository accountRepository;

    @Test
    void returnsNavigationSummaryForAuthenticatedUser() throws Exception {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(10L, "manager", AccountRole.MANAGER);
        when(jwtService.parseAccessToken("manager-token")).thenReturn(principal);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account(principal)));
        when(service.getSummary(any(CurrentAccountPrincipal.class)))
                .thenReturn(new NavigationSummaryResponse(1L, 2L, 3L, 4L, 0L));

        mockMvc.perform(get("/api/me/navigation-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingLeaveRequests").value(1))
                .andExpect(jsonPath("$.pendingPreferences").value(2))
                .andExpect(jsonPath("$.pendingSwapRequests").value(3))
                .andExpect(jsonPath("$.unreadNotifications").value(4))
                .andExpect(jsonPath("$.pendingSchedules").value(0));
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
}

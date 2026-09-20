package scheduling.scheduling.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
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
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeNotificationController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void listsNotifications() throws Exception {
        givenToken("employee-token", principal());
        when(notificationService.list(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class), eq(false))).thenReturn(List.of(new NotificationResponse(
                1L, "Title", "Content", "LEAVE_APPROVED", false, LocalDateTime.of(2026, 9, 20, 9, 0))));

        mockMvc.perform(get("/api/employee/notifications").param("read", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].notificationType").value("LEAVE_APPROVED"));
    }

    @Test
    void marksNotificationRead() throws Exception {
        givenToken("employee-token", principal());
        when(notificationService.markRead(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class), eq(1L))).thenReturn(new NotificationResponse(
                1L, "Title", "Content", "LEAVE_APPROVED", true, LocalDateTime.of(2026, 9, 20, 9, 0)));

        mockMvc.perform(patch("/api/employee/notifications/1/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void marksAllNotificationsRead() throws Exception {
        givenToken("employee-token", principal());

        mockMvc.perform(patch("/api/employee/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class));
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal principal() {
        return new CurrentAccountPrincipal(1L, "employee", AccountRole.EMPLOYEE);
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

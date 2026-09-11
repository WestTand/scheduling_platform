package scheduling.scheduling.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import scheduling.scheduling.admin.dto.AdminAccountEmployeeResponse;
import scheduling.scheduling.admin.dto.AdminAccountResponse;
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

@WebMvcTest(AdminAccountController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccountService adminAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsAccounts() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);
        when(adminAccountService.listAccounts("employee", "EMPLOYEE", "ACTIVE"))
                .thenReturn(List.of(accountResponse()));

        mockMvc.perform(get("/api/admin/accounts")
                        .param("query", "employee")
                        .param("role", "EMPLOYEE")
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].username").value("employee01"))
                .andExpect(jsonPath("$[0].employee.employeeCode").value("EMP001"));
    }

    @Test
    void managerLocksAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/admin/accounts/20/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminAccountService).lockAccount(20L);
    }

    @Test
    void managerUnlocksAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/admin/accounts/20/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminAccountService).unlockAccount(20L);
    }

    @Test
    void employeeCannotListAccounts() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(get("/api/admin/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminAccountService);
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

    private static AdminAccountResponse accountResponse() {
        return new AdminAccountResponse(
                20L,
                "employee01",
                AccountRole.EMPLOYEE,
                AccountStatus.ACTIVE,
                true,
                new AdminAccountEmployeeResponse(10L, "EMP001", "Nguyen Van A", "employee01@example.com", "ACTIVE"));
    }
}

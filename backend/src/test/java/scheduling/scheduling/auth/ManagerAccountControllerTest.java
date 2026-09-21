package scheduling.scheduling.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(ManagerAccountController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class ManagerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerAccountService managerAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerCreatesEmployeeAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);
        when(managerAccountService.createEmployeeAccount(same(manager), any(CreateEmployeeAccountRequest.class)))
                .thenReturn(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": 10,
                                  "username": "employee01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        verify(managerAccountService).createEmployeeAccount(same(manager), any(CreateEmployeeAccountRequest.class));
    }

    @Test
    void employeeCannotCreateEmployeeAccount() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee02", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": 10,
                                  "username": "employee01"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(managerAccountService);
    }

    @Test
    void managerResendsActivationOtp() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/manager/accounts/20/activation/resend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(managerAccountService).resendActivationOtp(20L);
    }

    @Test
    void managerResetsPassword() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        givenToken("manager-token", manager);

        mockMvc.perform(post("/api/manager/accounts/20/password/reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(managerAccountService).resetPassword(20L);
    }

    @Test
    void employeeCannotResendActivationOtp() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee02", AccountRole.EMPLOYEE);
        givenToken("employee-token", employee);

        mockMvc.perform(post("/api/manager/accounts/20/activation/resend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(managerAccountService);
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
}

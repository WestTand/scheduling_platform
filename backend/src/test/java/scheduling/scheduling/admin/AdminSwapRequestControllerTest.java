package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminSwapRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSwapRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSwapRequestService adminSwapRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsSwapRequests() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.listSwapRequests(any(CurrentAccountPrincipal.class), any()))
                .thenReturn(List.of(response("PENDING_APPROVAL")));

        mockMvc.perform(get("/api/admin/swap-requests")
                        .param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void managerApprovesSwapRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.approveSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/swap-requests/50/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(adminSwapRequestService).approveSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void managerRejectsSwapRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminSwapRequestService.rejectSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/api/admin/swap-requests/50/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(adminSwapRequestService).rejectSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void employeeCannotAccessAdminSwapApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSwapRequestService);
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

    private static SwapRequestResponse response(String status) {
        return new SwapRequestResponse(
                50L, 100L, LocalDate.of(2026, 9, 24),
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me", status, 1L, "manager01", null);
    }
}

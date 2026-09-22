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
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminPreferenceController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPreferenceService adminPreferenceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsPreferences() throws Exception {
        givenToken("manager-token", manager());
        when(adminPreferenceService.listPreferences(any(CurrentAccountPrincipal.class), any(), any(), any()))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/api/admin/preferences")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].employeeName").value("Nhan vien 1"))
                .andExpect(jsonPath("$[0].shiftCode").value("THONG_TAM"));
    }

    @Test
    void managerApprovesPreference() throws Exception {
        givenToken("manager-token", manager());
        when(adminPreferenceService.approvePreference(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/preferences/50/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(adminPreferenceService).approvePreference(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void managerRejectsPreference() throws Exception {
        givenToken("manager-token", manager());
        when(adminPreferenceService.rejectPreference(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/api/admin/preferences/50/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(adminPreferenceService).rejectPreference(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void employeeCannotAccessAdminPreferenceApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminPreferenceService);
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

    private static EmployeePreferenceResponse response(String status) {
        return new EmployeePreferenceResponse(
                50L,
                100L,
                "NV001",
                "Nhan vien 1",
                LocalDate.of(2026, 9, 14),
                "WANT",
                10L,
                "Morning",
                "THONG_TAM",
                1,
                "Prefer morning shift",
                status);
    }
}

package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeePreferenceController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeePreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeePreferenceService employeePreferenceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsPreferences() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.listPreferences(any(CurrentAccountPrincipal.class), any(), any(), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/preferences")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].preferenceType").value("WANT"));
    }

    @Test
    void employeeCreatesPreference() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.createPreference(any(CurrentAccountPrincipal.class), any(CreatePreferenceRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 1,
                                  "note": "Prefer morning shift"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20));

        ArgumentCaptor<CreatePreferenceRequest> requestCaptor = ArgumentCaptor.forClass(CreatePreferenceRequest.class);
        verify(employeePreferenceService).createPreference(any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().shiftTypeId()).isEqualTo(10L);
    }

    @Test
    void employeeUpdatesPreference() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.updatePreference(any(CurrentAccountPrincipal.class), any(Long.class), any(UpdatePreferenceRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/employee/preferences/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 1,
                                  "note": "Prefer morning shift"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenceType").value("WANT"));
    }

    @Test
    void createPreferenceRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceType": " ",
                                  "priority": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void updatePreferenceRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(put("/api/employee/preferences/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "priority": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void createPreferenceRejectsPriorityAboveFive() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 6
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void createPreferenceRejectsPriorityBelowOne() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void updatePreferenceRejectsPriorityAboveFive() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(put("/api/employee/preferences/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 6
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void updatePreferenceRejectsPriorityBelowOne() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(put("/api/employee/preferences/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void employeeCancelsPreference() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences/20/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(employeePreferenceService).cancelPreference(any(CurrentAccountPrincipal.class), org.mockito.ArgumentMatchers.eq(20L));
    }

    @Test
    void managerCannotAccessEmployeePreferenceApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeePreferenceService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
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

    private static EmployeePreferenceResponse response() {
        return new EmployeePreferenceResponse(
                20L,
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
                "PENDING");
    }
}

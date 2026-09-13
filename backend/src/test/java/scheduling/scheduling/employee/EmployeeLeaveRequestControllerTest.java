package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeLeaveRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeLeaveRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeLeaveRequestService employeeLeaveRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsLeaveRequests() throws Exception {
        givenToken("employee-token", employee());
        when(employeeLeaveRequestService.listLeaveRequests(
                any(CurrentAccountPrincipal.class), any(), any(), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/leave-requests")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void employeeCreatesLeaveRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeLeaveRequestService.createLeaveRequest(any(CurrentAccountPrincipal.class), any(CreateLeaveRequestRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-15",
                                  "reason": "Family appointment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<CreateLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(CreateLeaveRequestRequest.class);
        verify(employeeLeaveRequestService).createLeaveRequest(any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void createLeaveRequestRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endDate": "2026-09-15"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeLeaveRequestService);
    }

    @Test
    void employeeCancelsLeaveRequest() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/leave-requests/10/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(employeeLeaveRequestService).cancelLeaveRequest(any(CurrentAccountPrincipal.class), org.mockito.ArgumentMatchers.eq(10L));
    }

    @Test
    void managerCannotAccessEmployeeLeaveApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeLeaveRequestService);
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

    private static EmployeeLeaveRequestResponse response() {
        return new EmployeeLeaveRequestResponse(
                10L,
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                "Family appointment",
                "PENDING");
    }
}

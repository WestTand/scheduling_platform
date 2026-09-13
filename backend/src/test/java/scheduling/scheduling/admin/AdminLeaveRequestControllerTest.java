package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
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
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
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

@WebMvcTest(AdminLeaveRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminLeaveRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminLeaveRequestService adminLeaveRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsLeaveRequestsWithFilters() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.listLeaveRequests(
                any(CurrentAccountPrincipal.class), eq(7L), eq("PENDING"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/api/admin/leave-requests")
                        .param("employeeId", "7")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].employeeCode").value("E001"));

        ArgumentCaptor<CurrentAccountPrincipal> principalCaptor = ArgumentCaptor.forClass(CurrentAccountPrincipal.class);
        verify(adminLeaveRequestService).listLeaveRequests(
                principalCaptor.capture(), eq(7L), eq("PENDING"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
        assertThat(principalCaptor.getValue().accountId()).isEqualTo(1L);
    }

    @Test
    void nonManagerCannotListLeaveRequests() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminLeaveRequestService);
    }

    @Test
    void anonymousCannotListLeaveRequests() throws Exception {
        mockMvc.perform(get("/api/admin/leave-requests"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminLeaveRequestService);
    }

    @Test
    void managerApprovesLeaveRequestWithNote() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.approveLeaveRequest(
                any(CurrentAccountPrincipal.class), eq(10L), any(ReviewLeaveRequestRequest.class)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/leave-requests/10/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "Approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<ReviewLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(ReviewLeaveRequestRequest.class);
        verify(adminLeaveRequestService).approveLeaveRequest(any(CurrentAccountPrincipal.class), eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reviewNote()).isEqualTo("Approved");
    }

    @Test
    void managerApprovesLeaveRequestWithNoBody() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.approveLeaveRequest(
                any(CurrentAccountPrincipal.class), eq(10L), any(ReviewLeaveRequestRequest.class)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/leave-requests/10/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<ReviewLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(ReviewLeaveRequestRequest.class);
        verify(adminLeaveRequestService).approveLeaveRequest(any(CurrentAccountPrincipal.class), eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reviewNote()).isNull();
    }

    @Test
    void managerRejectsLeaveRequestWithNote() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.rejectLeaveRequest(
                any(CurrentAccountPrincipal.class), eq(10L), any(ReviewLeaveRequestRequest.class)))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/api/admin/leave-requests/10/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "Coverage is not enough"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        ArgumentCaptor<ReviewLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(ReviewLeaveRequestRequest.class);
        verify(adminLeaveRequestService).rejectLeaveRequest(any(CurrentAccountPrincipal.class), eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reviewNote()).isEqualTo("Coverage is not enough");
    }

    @Test
    void reviewLeaveRequestRejectsOverlongNote() throws Exception {
        givenToken("manager-token", manager());
        String longNote = "a".repeat(1001);

        mockMvc.perform(post("/api/admin/leave-requests/10/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "%s"
                                }
                                """.formatted(longNote)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminLeaveRequestService);
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

    private static AdminLeaveRequestResponse response(String status) {
        return new AdminLeaveRequestResponse(
                10L,
                7L,
                "E001",
                "Nguyen Van A",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                "Family appointment",
                status,
                "Approved",
                1L,
                "manager01",
                LocalDateTime.of(2026, 9, 13, 10, 0));
    }
}

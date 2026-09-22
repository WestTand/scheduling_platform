package scheduling.scheduling.employee;

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
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeSwapRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeSwapRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeSwapRequestService employeeSwapRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsRelatedSwapRequests() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.listSwapRequests(any(CurrentAccountPrincipal.class), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/swap-requests")
                        .param("status", "PENDING_TARGET")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("PENDING_TARGET"))
                .andExpect(jsonPath("$[0].targetEmployee.employeeCode").value("NV9"));
    }

    @Test
    void employeeCreatesSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.createSwapRequest(
                any(CurrentAccountPrincipal.class), any(CreateSwapRequestRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requesterAssignmentId": 100,
                                  "targetEmployeeId": 9,
                                  "reason": "Cover me"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50));

        ArgumentCaptor<CreateSwapRequestRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateSwapRequestRequest.class);
        verify(employeeSwapRequestService).createSwapRequest(
                any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().requesterAssignmentId()).isEqualTo(100L);
    }

    @Test
    void createSwapRequestRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEmployeeId": 9
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeSwapRequestService);
    }

    @Test
    void employeeCancelsOwnSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.cancelSwapRequest(any(CurrentAccountPrincipal.class), eq(50L)))
                .thenReturn(cancelledResponse());

        mockMvc.perform(post("/api/employee/swap-requests/50/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(employeeSwapRequestService).cancelSwapRequest(any(CurrentAccountPrincipal.class), eq(50L));
    }

    @Test
    void targetRespondsToSwapRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeSwapRequestService.respondSwapRequest(
                any(CurrentAccountPrincipal.class), eq(50L), any(RespondSwapRequestRequest.class)))
                .thenReturn(pendingApprovalResponse());

        mockMvc.perform(post("/api/employee/swap-requests/50/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accept": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        ArgumentCaptor<RespondSwapRequestRequest> requestCaptor =
                ArgumentCaptor.forClass(RespondSwapRequestRequest.class);
        verify(employeeSwapRequestService).respondSwapRequest(
                any(CurrentAccountPrincipal.class), eq(50L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().accept()).isTrue();
    }

    @Test
    void respondSwapRequestRejectsMissingAccept() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/swap-requests/50/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeSwapRequestService);
    }

    @Test
    void managerCannotAccessEmployeeSwapApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/swap-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeSwapRequestService);
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

    private static SwapRequestResponse response() {
        return new SwapRequestResponse(
                50L, 100L, LocalDate.of(2026, 9, 24),
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me", "PENDING_TARGET", null, null, null);
    }

    private static SwapRequestResponse cancelledResponse() {
        return new SwapRequestResponse(
                50L, 100L, LocalDate.of(2026, 9, 24),
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me", "CANCELLED", null, null, null);
    }

    private static SwapRequestResponse pendingApprovalResponse() {
        return new SwapRequestResponse(
                50L, 100L, LocalDate.of(2026, 9, 24),
                new SwapRequestResponse.EmployeeSummary(7L, "NV7", "Nhan vien 7"),
                null,
                new SwapRequestResponse.EmployeeSummary(9L, "NV9", "Nhan vien 9"),
                "Cover me", "PENDING_APPROVAL", null, null, null);
    }
}

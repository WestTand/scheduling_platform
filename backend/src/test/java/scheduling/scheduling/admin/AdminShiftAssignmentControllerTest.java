package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.admin.dto.UpdateShiftAssignmentRequest;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminShiftAssignmentController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminShiftAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShiftAssignmentService adminShiftAssignmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerAssignsEmployee() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.createAssignment(
                any(CurrentAccountPrincipal.class), eq(200L), any(CreateShiftAssignmentRequest.class)))
                .thenReturn(assignment(false));

        mockMvc.perform(post("/api/admin/work-shifts/200/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId": 7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.assignedSource").value("MANUAL"));

        ArgumentCaptor<CurrentAccountPrincipal> principalCaptor =
                ArgumentCaptor.forClass(CurrentAccountPrincipal.class);
        ArgumentCaptor<CreateShiftAssignmentRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateShiftAssignmentRequest.class);
        verify(adminShiftAssignmentService)
                .createAssignment(principalCaptor.capture(), eq(200L), requestCaptor.capture());
        assertThat(principalCaptor.getValue().accountId()).isEqualTo(1L);
        assertThat(requestCaptor.getValue().employeeId()).isEqualTo(7L);
    }

    @Test
    void managerViewsPeriodSchedule() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.getSchedule(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(new AdminPeriodScheduleResponse(
                        20L, "Week 38", "DRAFT",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), List.of(), null, List.of()));

        mockMvc.perform(get("/api/admin/schedule-periods/20/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(20));

        verify(adminShiftAssignmentService).getSchedule(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void managerDeletesAssignment() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(delete("/api/admin/assignments/500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminShiftAssignmentService).deleteAssignment(any(CurrentAccountPrincipal.class), eq(500L));
    }

    @Test
    void managerUpdatesAssignmentEmployee() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.updateAssignment(
                any(CurrentAccountPrincipal.class), eq(500L), any(UpdateShiftAssignmentRequest.class)))
                .thenReturn(assignment(false));

        mockMvc.perform(put("/api/admin/assignments/500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId": 8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.employeeId").value(7));

        ArgumentCaptor<UpdateShiftAssignmentRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateShiftAssignmentRequest.class);
        verify(adminShiftAssignmentService)
                .updateAssignment(any(CurrentAccountPrincipal.class), eq(500L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().employeeId()).isEqualTo(8L);
    }

    @Test
    void managerLocksAssignment() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.lockAssignment(any(CurrentAccountPrincipal.class), eq(500L)))
                .thenReturn(assignment(true));

        mockMvc.perform(patch("/api/admin/assignments/500/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        verify(adminShiftAssignmentService).lockAssignment(any(CurrentAccountPrincipal.class), eq(500L));
    }

    @Test
    void managerUnlocksAssignment() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.unlockAssignment(any(CurrentAccountPrincipal.class), eq(500L)))
                .thenReturn(assignment(false));

        mockMvc.perform(patch("/api/admin/assignments/500/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        verify(adminShiftAssignmentService).unlockAssignment(any(CurrentAccountPrincipal.class), eq(500L));
    }

    @Test
    void createRejectsMissingEmployeeId() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/work-shifts/200/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminShiftAssignmentService);
    }

    @Test
    void nonManagerCannotAssign() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/admin/work-shifts/200/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId": 7}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminShiftAssignmentService);
    }

    @Test
    void anonymousCannotAssign() throws Exception {
        mockMvc.perform(post("/api/admin/work-shifts/200/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId": 7}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminShiftAssignmentService);
    }

    @Test
    void managerViewsEmployeeSchedule() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.getEmployeeSchedule(
                        any(CurrentAccountPrincipal.class), eq(7L),
                        eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(scheduleItem()));

        mockMvc.perform(get("/api/admin/employees/7/schedule")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(300))
                .andExpect(jsonPath("$[0].periodStatus").value("PUBLISHED"));

        verify(adminShiftAssignmentService).getEmployeeSchedule(
                any(CurrentAccountPrincipal.class), eq(7L),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void managerViewsEmployeeScheduleWithoutDates() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.getEmployeeSchedule(
                        any(CurrentAccountPrincipal.class), eq(7L), eq(null), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/employees/7/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(adminShiftAssignmentService).getEmployeeSchedule(
                any(CurrentAccountPrincipal.class), eq(7L), eq(null), eq(null));
    }

    @Test
    void employeeCannotViewScheduleByEmployee() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/employees/7/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminShiftAssignmentService);
    }

    @Test
    void anonymousCannotViewScheduleByEmployee() throws Exception {
        mockMvc.perform(get("/api/admin/employees/7/schedule"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminShiftAssignmentService);
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

    private static AdminShiftAssignmentResponse assignment(boolean locked) {
        return new AdminShiftAssignmentResponse(
                500L, 200L, 20L, 7L, "NV007", "Nguyen Van A", "ASSIGNED", "MANUAL", locked);
    }

    private static EmployeeScheduleItemResponse scheduleItem() {
        return new EmployeeScheduleItemResponse(
                300L,
                "ASSIGNED",
                "MANUAL",
                false,
                LocalDate.of(2026, 9, 17),
                100L,
                "Day",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                OffsetDateTime.parse("2026-09-17T08:00:00+07:00"),
                OffsetDateTime.parse("2026-09-17T16:00:00+07:00"),
                480,
                false,
                20L,
                "Week 38",
                "PUBLISHED");
    }
}

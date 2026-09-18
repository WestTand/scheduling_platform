package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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

@WebMvcTest(EmployeeScheduleController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeScheduleService employeeScheduleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeViewsOwnSchedule() throws Exception {
        givenToken("employee-token", employee());
        when(employeeScheduleService.getOwnSchedule(
                        any(CurrentAccountPrincipal.class),
                        eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/schedule")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(300))
                .andExpect(jsonPath("$[0].periodStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-09-17T08:00:00+07:00"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-09-17T16:00:00+07:00"))
                .andExpect(jsonPath("$[0].workMinutes").value(480))
                .andExpect(jsonPath("$[0].isTransitionShift").value(false));

        ArgumentCaptor<CurrentAccountPrincipal> principalCaptor =
                ArgumentCaptor.forClass(CurrentAccountPrincipal.class);
        verify(employeeScheduleService).getOwnSchedule(
                principalCaptor.capture(), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
        assertThat(principalCaptor.getValue().accountId()).isEqualTo(2L);
    }

    @Test
    void employeeViewsOwnScheduleWithoutDates() throws Exception {
        givenToken("employee-token", employee());
        when(employeeScheduleService.getOwnSchedule(
                        any(CurrentAccountPrincipal.class), eq(null), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/employee/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(employeeScheduleService).getOwnSchedule(
                any(CurrentAccountPrincipal.class), eq(null), eq(null));
    }

    @Test
    void managerCannotAccessEmployeeSchedule() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeScheduleService);
    }

    @Test
    void anonymousCannotAccessEmployeeSchedule() throws Exception {
        mockMvc.perform(get("/api/employee/schedule"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(employeeScheduleService);
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

    private static EmployeeScheduleItemResponse response() {
        return new EmployeeScheduleItemResponse(300L,"ASSIGNED","MANUAL",false,LocalDate.of(2026,9,17),100L,"Day",LocalTime.of(8,0),LocalTime.of(16,0),OffsetDateTime.parse("2026-09-17T08:00:00+07:00"),OffsetDateTime.parse("2026-09-17T16:00:00+07:00"),480,false,20L,"Week 38","PUBLISHED");
    }
}

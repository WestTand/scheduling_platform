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
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
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

@WebMvcTest(AdminWorkShiftController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminWorkShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminWorkShiftService adminWorkShiftService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerCreatesWorkShift() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.createWorkShift(
                any(CurrentAccountPrincipal.class), eq(20L), any(CreateWorkShiftRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftTypeId": 100,
                                  "workDate": "2026-09-14",
                                  "requiredStaff": 2,
                                  "note": "Morning coverage"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.periodId").value(20))
                .andExpect(jsonPath("$.shiftTypeId").value(100));

        ArgumentCaptor<CurrentAccountPrincipal> principalCaptor = ArgumentCaptor.forClass(CurrentAccountPrincipal.class);
        ArgumentCaptor<CreateWorkShiftRequest> requestCaptor = ArgumentCaptor.forClass(CreateWorkShiftRequest.class);
        verify(adminWorkShiftService).createWorkShift(principalCaptor.capture(), eq(20L), requestCaptor.capture());
        assertThat(principalCaptor.getValue().accountId()).isEqualTo(1L);
        assertThat(requestCaptor.getValue().shiftTypeId()).isEqualTo(100L);
        assertThat(requestCaptor.getValue().workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void managerListsWorkShiftsWithFilters() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.listWorkShifts(
                any(CurrentAccountPrincipal.class), eq(20L), eq(LocalDate.of(2026, 9, 14)), eq(100L)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts")
                        .param("date", "2026-09-14")
                        .param("shiftTypeId", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(200))
                .andExpect(jsonPath("$[0].workDate").value("2026-09-14"))
                .andExpect(jsonPath("$[0].shiftStartTime").value("08:00:00"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-09-14T08:00:00+07:00"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-09-14T16:00:00+07:00"))
                .andExpect(jsonPath("$[0].workMinutes").value(480))
                .andExpect(jsonPath("$[0].isTransitionShift").value(false));

        verify(adminWorkShiftService).listWorkShifts(
                any(CurrentAccountPrincipal.class), eq(20L), eq(LocalDate.of(2026, 9, 14)), eq(100L));
    }

    @Test
    void managerGetsWorkShiftDetail() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.getWorkShift(any(CurrentAccountPrincipal.class), eq(200L))).thenReturn(response());

        mockMvc.perform(get("/api/admin/work-shifts/200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.shiftName").value("Morning"));

        verify(adminWorkShiftService).getWorkShift(any(CurrentAccountPrincipal.class), eq(200L));
    }

    @Test
    void createWorkShiftRejectsInvalidBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "2026-09-14",
                                  "requiredStaff": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminWorkShiftService);
    }

    @Test
    void nonManagerCannotListWorkShifts() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminWorkShiftService);
    }

    @Test
    void anonymousCannotListWorkShifts() throws Exception {
        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminWorkShiftService);
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

    private static AdminWorkShiftResponse response() {
        return new AdminWorkShiftResponse(
                200L, 20L, "Week 38", 100L, "Morning",
                LocalTime.of(8, 0), LocalTime.of(16, 0),
                LocalDate.of(2026, 9, 14),
                OffsetDateTime.parse("2026-09-14T08:00:00+07:00"),
                OffsetDateTime.parse("2026-09-14T16:00:00+07:00"),
                480, false, 2, "Morning coverage");
    }
}

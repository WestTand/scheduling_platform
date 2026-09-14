package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
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

@WebMvcTest(AdminSchedulePeriodController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSchedulePeriodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSchedulePeriodService adminSchedulePeriodService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerCreatesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.createSchedulePeriod(
                any(CurrentAccountPrincipal.class), any(SchedulePeriodRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "Week 38",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<SchedulePeriodRequest> requestCaptor = ArgumentCaptor.forClass(SchedulePeriodRequest.class);
        verify(adminSchedulePeriodService).createSchedulePeriod(
                any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().periodName()).isEqualTo("Week 38");
        assertThat(requestCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void managerCreatesSchedulePeriodWhenTrimmedPeriodNameIsAtMaxLength() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.createSchedulePeriod(
                any(CurrentAccountPrincipal.class), any(SchedulePeriodRequest.class)))
                .thenReturn(response());
        String periodName = " " + "A".repeat(100) + " ";

        mockMvc.perform(post("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "%s",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """.formatted(periodName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20));

        ArgumentCaptor<SchedulePeriodRequest> requestCaptor = ArgumentCaptor.forClass(SchedulePeriodRequest.class);
        verify(adminSchedulePeriodService).createSchedulePeriod(
                any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().periodName()).isEqualTo(periodName);
    }

    @Test
    void managerListsSchedulePeriodsWithFilters() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/schedule-periods")
                        .param("status", "DRAFT")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20));

        verify(adminSchedulePeriodService).listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void managerListsSchedulePeriodsWithoutStatusAsDraft() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"), eq(null), eq(null)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20));

        verify(adminSchedulePeriodService).listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"), eq(null), eq(null));
    }

    @Test
    void managerGetsSchedulePeriodDetail() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.getSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(response());

        mockMvc.perform(get("/api/admin/schedule-periods/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodName").value("Week 38"));

        verify(adminSchedulePeriodService).getSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void managerUpdatesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.updateSchedulePeriod(
                any(CurrentAccountPrincipal.class), eq(20L), any(SchedulePeriodRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/admin/schedule-periods/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "Week 38",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(adminSchedulePeriodService).updateSchedulePeriod(
                any(CurrentAccountPrincipal.class), eq(20L), any(SchedulePeriodRequest.class));
    }

    @Test
    void createSchedulePeriodRejectsInvalidRequestBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void nonManagerCannotListSchedulePeriods() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void anonymousCannotListSchedulePeriods() throws Exception {
        mockMvc.perform(get("/api/admin/schedule-periods"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminSchedulePeriodService);
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

    private static AdminSchedulePeriodResponse response() {
        return new AdminSchedulePeriodResponse(
                20L,
                "Week 38",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 20),
                1,
                "DRAFT",
                1L,
                "manager01",
                null,
                null,
                null,
                null);
    }
}

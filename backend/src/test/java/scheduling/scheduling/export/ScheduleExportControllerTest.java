package scheduling.scheduling.export;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(ScheduleExportController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class ScheduleExportControllerTest {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private ScheduleExportService scheduleExportService;

    @MockitoBean
    private ScheduleExcelWriter scheduleExcelWriter;

    @MockitoBean
    private SchedulePdfWriter schedulePdfWriter;

    @MockitoBean
    private WorkloadReportService workloadReportService;

    @MockitoBean
    private WorkloadExcelWriter workloadExcelWriter;

    @Test
    void managerDownloadsScheduleXlsx() throws Exception {
        givenToken("manager-token", manager());
        ScheduleExportData exportData = scheduleExportData();
        when(scheduleExportService.buildScheduleExport(20L)).thenReturn(exportData);
        when(scheduleExcelWriter.write(exportData)).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule-period-20.xlsx\""))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void adminDownloadsSchedulePdf() throws Exception {
        givenToken("admin-token", admin());
        ScheduleExportData exportData = scheduleExportData();
        when(scheduleExportService.buildScheduleExport(20L)).thenReturn(exportData);
        when(schedulePdfWriter.write(exportData)).thenReturn(new byte[] {4, 5, 6});

        mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule-period-20.pdf\""))
                .andExpect(content().bytes(new byte[] {4, 5, 6}));
    }

    @Test
    void managerDownloadsWorkloadXlsx() throws Exception {
        givenToken("manager-token", manager());
        WorkloadReportData reportData = workloadReportData();
        when(workloadReportService.buildWorkloadReport(20L)).thenReturn(reportData);
        when(workloadExcelWriter.write(reportData)).thenReturn(new byte[] {7, 8, 9});

        mockMvc.perform(get("/api/admin/schedule-periods/20/reports/workload.xlsx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"workload-period-20.xlsx\""))
                .andExpect(content().bytes(new byte[] {7, 8, 9}));
    }

    @Test
    void employeeCannotDownloadScheduleXlsx() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleExportService, scheduleExcelWriter, schedulePdfWriter,
                workloadReportService, workloadExcelWriter);
    }

    @Test
    void anonymousCannotDownloadScheduleXlsx() throws Exception {
        mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduleExportService, scheduleExcelWriter, schedulePdfWriter,
                workloadReportService, workloadExcelWriter);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal admin() {
        return new CurrentAccountPrincipal(2L, "admin01", AccountRole.ADMIN);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(3L, "employee01", AccountRole.EMPLOYEE);
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

    private static ScheduleExportData scheduleExportData() {
        return new ScheduleExportData(
                20L,
                "Week 38",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 20),
                1,
                "DRAFT",
                LocalDateTime.of(2026, 9, 20, 12, 0),
                List.of());
    }

    private static WorkloadReportData workloadReportData() {
        return new WorkloadReportData(
                20L,
                "Week 38",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 20),
                1,
                "DRAFT",
                LocalDateTime.of(2026, 9, 20, 12, 0),
                List.of());
    }
}

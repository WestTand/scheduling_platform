package scheduling.scheduling.admin;

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

import java.math.BigDecimal;
import java.time.LocalTime;
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
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;
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

@WebMvcTest(AdminShiftTypeController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminShiftTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShiftTypeService adminShiftTypeService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsShiftTypes() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.listShiftTypes("ngoai", "ACTIVE"))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/shift-types")
                        .param("query", "ngoai")
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].shiftName").value("Ngoai gio"))
                .andExpect(jsonPath("$[0].quotaCategory").value("OVERTIME"));
    }

    @Test
    void managerCreatesShiftType() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.createShiftType(any(CreateShiftTypeRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/admin/shift-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": "Ngoai gio",
                                  "startTime": "08:00:00",
                                  "endTime": "12:00:00",
                                  "durationHours": 4.0,
                                  "minStaff": 1,
                                  "maxStaff": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.quotaCategory").value("OVERTIME"));

        ArgumentCaptor<CreateShiftTypeRequest> requestCaptor = ArgumentCaptor.forClass(CreateShiftTypeRequest.class);
        verify(adminShiftTypeService).createShiftType(requestCaptor.capture());
        CreateShiftTypeRequest request = requestCaptor.getValue();
        assertThat(request.shiftName()).isEqualTo("Ngoai gio");
        assertThat(request.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.durationHours()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
        assertThat(request.minStaff()).isEqualTo(1);
        assertThat(request.maxStaff()).isEqualTo(3);
    }

    @Test
    void createShiftTypeRejectsInvalidBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/shift-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": " ",
                                  "startTime": "08:00:00",
                                  "durationHours": 0,
                                  "minStaff": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminShiftTypeService);
    }

    @Test
    void managerUpdatesShiftType() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.updateShiftType(any(Long.class), any(UpdateShiftTypeRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/admin/shift-types/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": "Ngoai gio",
                                  "startTime": "08:00:00",
                                  "endTime": "12:00:00",
                                  "durationHours": 4.0,
                                  "overnight": false,
                                  "heavy": false,
                                  "minStaff": 1,
                                  "maxStaff": 3,
                                  "status": "ACTIVE",
                                  "quotaCategory": "OVERTIME"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftName").value("Ngoai gio"))
                .andExpect(jsonPath("$.quotaCategory").value("OVERTIME"));

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<UpdateShiftTypeRequest> requestCaptor = ArgumentCaptor.forClass(UpdateShiftTypeRequest.class);
        verify(adminShiftTypeService).updateShiftType(idCaptor.capture(), requestCaptor.capture());
        UpdateShiftTypeRequest request = requestCaptor.getValue();
        assertThat(idCaptor.getValue()).isEqualTo(10L);
        assertThat(request.shiftName()).isEqualTo("Ngoai gio");
        assertThat(request.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(request.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(request.durationHours()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
        assertThat(request.overnight()).isFalse();
        assertThat(request.heavy()).isFalse();
        assertThat(request.minStaff()).isEqualTo(1);
        assertThat(request.maxStaff()).isEqualTo(3);
        assertThat(request.status()).isEqualTo("ACTIVE");
        assertThat(request.quotaCategory()).isEqualTo("OVERTIME");
    }

    @Test
    void updateShiftTypeRejectsInvalidBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(put("/api/admin/shift-types/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": " ",
                                  "startTime": "08:00:00",
                                  "durationHours": 0,
                                  "overnight": null,
                                  "heavy": null,
                                  "minStaff": 0,
                                  "status": " ",
                                  "quotaCategory": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminShiftTypeService);
    }

    @Test
    void managerLocksAndUnlocksShiftType() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/shift-types/10/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/shift-types/10/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminShiftTypeService).lockShiftType(10L);
        verify(adminShiftTypeService).unlockShiftType(10L);
    }

    @Test
    void employeeCannotAccessShiftTypeApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/shift-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminShiftTypeService);
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

    private static AdminShiftTypeResponse response() {
        return new AdminShiftTypeResponse(
                10L,
                "Ngoai gio",
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                false,
                false,
                1,
                3,
                "OVERTIME",
                "ACTIVE");
    }
}

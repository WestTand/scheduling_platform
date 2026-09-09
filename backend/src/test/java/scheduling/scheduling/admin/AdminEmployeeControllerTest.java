package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminEmployeeAccountResponse;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.admin.dto.UpdateEmployeeRequest;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;

@WebMvcTest(AdminEmployeeController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminEmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEmployeeService adminEmployeeService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @Test
    void managerListsEmployees() throws Exception {
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager());
        when(adminEmployeeService.listEmployees("nguyen", "ACTIVE"))
                .thenReturn(List.of(employeeResponse()));

        mockMvc.perform(get("/api/admin/employees")
                        .param("query", "nguyen")
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].employeeCode").value("EMP001"))
                .andExpect(jsonPath("$[0].account.username").value("employee01"));
    }

    @Test
    void managerGetsEmployeeDetail() throws Exception {
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager());
        when(adminEmployeeService.getEmployee(10L)).thenReturn(employeeResponse());

        mockMvc.perform(get("/api/admin/employees/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    void managerUpdatesEmployee() throws Exception {
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager());
        when(adminEmployeeService.updateEmployee(any(Long.class), any(UpdateEmployeeRequest.class)))
                .thenReturn(employeeResponse());

        mockMvc.perform(put("/api/admin/employees/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Van A",
                                  "email": "employee01@example.com",
                                  "phone": "0900000001",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void managerLocksEmployee() throws Exception {
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager());

        mockMvc.perform(post("/api/admin/employees/10/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminEmployeeService).lockEmployee(10L);
    }

    @Test
    void managerUnlocksEmployee() throws Exception {
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager());

        mockMvc.perform(post("/api/admin/employees/10/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminEmployeeService).unlockEmployee(10L);
    }

    @Test
    void employeeCannotAccessAdminEmployeeApis() throws Exception {
        when(jwtService.parseAccessToken("employee-token")).thenReturn(employee());

        mockMvc.perform(get("/api/admin/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminEmployeeService);
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static AdminEmployeeResponse employeeResponse() {
        return new AdminEmployeeResponse(
                10L,
                "EMP001",
                "Nguyen Van A",
                "employee01@example.com",
                "0900000001",
                "ACTIVE",
                new AdminEmployeeAccountResponse(20L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true));
    }
}

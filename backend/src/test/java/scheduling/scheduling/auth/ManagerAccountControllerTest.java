package scheduling.scheduling.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;
import scheduling.scheduling.entity.AccountRole;

@WebMvcTest(ManagerAccountController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class ManagerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerAccountService managerAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @Test
    void managerCreatesEmployeeAccount() throws Exception {
        CurrentAccountPrincipal manager = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(jwtService.parseAccessToken("manager-token")).thenReturn(manager);
        when(managerAccountService.createEmployeeAccount(any(CreateEmployeeAccountRequest.class)))
                .thenReturn(new AccountResponse(20L, "employee01", AccountRole.EMPLOYEE));

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A",
                                  "email": "employee01@gmail.com",
                                  "phone": "0900000001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        verify(managerAccountService).createEmployeeAccount(any(CreateEmployeeAccountRequest.class));
    }

    @Test
    void employeeCannotCreateEmployeeAccount() throws Exception {
        CurrentAccountPrincipal employee = new CurrentAccountPrincipal(2L, "employee02", AccountRole.EMPLOYEE);
        when(jwtService.parseAccessToken("employee-token")).thenReturn(employee);

        mockMvc.perform(post("/api/manager/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(managerAccountService);
    }
}

package scheduling.scheduling.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.ActivationStateResponse;
import scheduling.scheduling.auth.dto.AuthResponse;
import scheduling.scheduling.auth.dto.EmployeeResponse;
import scheduling.scheduling.auth.dto.LoginRequest;
import scheduling.scheduling.auth.dto.LoginStateResponse;
import scheduling.scheduling.auth.dto.MeResponse;
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
import scheduling.scheduling.auth.dto.VerifyActivationOtpRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void corsPreflightAllowsFrontendOriginForLogin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsString("content-type")));
    }

    @Test
    void registerEndpointIsNotAvailable() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "password": "secret123",
                                  "employeeCode": "EMP001",
                                  "fullName": "Nguyen Van A",
                                  "email": "employee01@example.com",
                                  "phone": "0900000001"
                                }
                                """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void loginSetsHttpOnlyRefreshCookie() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResult(authResponse("access-token", "employee01"), "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-token"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("HttpOnly"))));
    }

    @Test
    void loginReturnsOtpRequiredWithoutRefreshCookie() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(LoginResult.state(new LoginStateResponse("OTP_REQUIRED", 42L, "employee01")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OTP_REQUIRED"))
                .andExpect(jsonPath("$.accountId").value(42))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginReturnsPasswordChangeRequiredWithoutRefreshCookie() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(LoginResult.state(new LoginStateResponse("PASSWORD_CHANGE_REQUIRED", 42L, "employee01")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.accountId").value(42))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void verifyActivationOtpReturnsPasswordChangeRequiredWithoutRefreshCookie() throws Exception {
        when(authService.verifyActivationOtp(any(VerifyActivationOtpRequest.class)))
                .thenReturn(new ActivationStateResponse("PASSWORD_CHANGE_REQUIRED", 42L, "employee01"));

        mockMvc.perform(post("/api/auth/activation/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 42,
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.accountId").value(42))
                .andExpect(jsonPath("$.username").value("employee01"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void resendActivationOtpReturnsNoContentWithoutRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "temporaryPassword": "secret123"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verify(authService).resendActivationOtp(any(ResendActivationOtpRequest.class));
    }

    @Test
    void resendActivationOtpRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "temporaryPassword": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void resendActivationOtpIgnoresInvalidBearerToken() throws Exception {
        when(jwtService.parseAccessToken("bad-token")).thenThrow(new InvalidTokenException("Invalid access token"));

        mockMvc.perform(post("/api/auth/activation/resend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "employee01",
                                  "temporaryPassword": "secret123"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verify(authService).resendActivationOtp(any(ResendActivationOtpRequest.class));
    }

    @Test
    void verifyActivationOtpRejectsInvalidOtp() throws Exception {
        when(authService.verifyActivationOtp(any(VerifyActivationOtpRequest.class)))
                .thenThrow(new InvalidActivationOtpException());

        mockMvc.perform(post("/api/auth/activation/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 42,
                                  "otp": "000000"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVATION_OTP"))
                .andExpect(jsonPath("$.message").value("Invalid activation OTP"));
    }

    @Test
    void changeRequiredPasswordReturnsTokensAndRefreshCookie() throws Exception {
        when(authService.changeRequiredPassword(any(RequiredPasswordChangeRequest.class)))
                .thenReturn(new LoginResult(authResponse("access-token", "employee01"), "refresh-token"));

        mockMvc.perform(post("/api/auth/password/change-required")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {
                                   "accountId": 42,
                                   "currentPassword": "old-password",
                                   "newPassword": "new-password"
                                 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-token"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("HttpOnly"))));
    }

    @Test
    void changeRequiredPasswordRejectsInvalidState() throws Exception {
        when(authService.changeRequiredPassword(any(RequiredPasswordChangeRequest.class)))
                .thenThrow(new InvalidPasswordChangeStateException());

        mockMvc.perform(post("/api/auth/password/change-required")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {
                                   "accountId": 42,
                                   "currentPassword": "old-password",
                                   "newPassword": "new-password"
                                 }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_CHANGE_STATE"))
                .andExpect(jsonPath("$.message").value("Invalid password change state"));
    }

    @Test
    void changeRequiredPasswordIgnoresInvalidBearerToken() throws Exception {
        when(jwtService.parseAccessToken("bad-token")).thenThrow(new InvalidTokenException("Invalid access token"));
        when(authService.changeRequiredPassword(any(RequiredPasswordChangeRequest.class)))
                .thenReturn(new LoginResult(authResponse("access-token", "employee01"), "refresh-token"));

        mockMvc.perform(post("/api/auth/password/change-required")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 42,
                                  "currentPassword": "old-password",
                                  "newPassword": "new-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refreshReadsCookieAndSetsRotatedCookie() throws Exception {
        when(authService.refresh("old-refresh-token"))
                .thenReturn(new LoginResult(authResponse("new-access-token", "employee01"), "new-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token=new-refresh-token"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("HttpOnly"))));
        verify(authService).refresh("old-refresh-token");
    }

    @Test
    void refreshIgnoresInvalidBearerTokenWhenRefreshCookieIsValid() throws Exception {
        when(jwtService.parseAccessToken("bad-token")).thenThrow(new InvalidTokenException("Invalid access token"));
        when(authService.refresh("old-refresh-token"))
                .thenReturn(new LoginResult(authResponse("new-access-token", "employee01"), "new-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
        verify(authService).refresh("old-refresh-token");
    }

    @Test
    void refreshRejectsMissingCookieWithInvalidTokenResponse() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void logoutClearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token="),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("HttpOnly"))));
        verify(authService).logout("refresh-token");
    }

    @Test
    void meRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsCurrentUserWithBearerToken() throws Exception {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(42L, "employee01", AccountRole.EMPLOYEE);
        when(jwtService.parseAccessToken("valid-access-token")).thenReturn(principal);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account(42L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true)));
        when(authService.me(principal)).thenReturn(new MeResponse(
                new AccountResponse(42L, "employee01", AccountRole.EMPLOYEE),
                null));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(42))
                .andExpect(jsonPath("$.account.username").value("employee01"))
                .andExpect(jsonPath("$.account.role").value("EMPLOYEE"));
        verify(authService).me(principal);
    }

    @Test
    void meRejectsBearerTokenForInactiveAccount() throws Exception {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(42L, "employee01", AccountRole.EMPLOYEE);
        when(jwtService.parseAccessToken("stale-access-token")).thenReturn(principal);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account(42L, "employee01", AccountRole.EMPLOYEE, AccountStatus.INACTIVE, false)));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid access token"));

        verifyNoInteractions(authService);
    }

    private static AuthResponse authResponse(String accessToken, String username) {
        return new AuthResponse(
                accessToken,
                "Bearer",
                900,
                new AccountResponse(42L, username, AccountRole.EMPLOYEE));
    }

    private static Account account(Long id, String username, AccountRole role, AccountStatus status, boolean active) {
        Account account = Account.builder()
                .username(username)
                .passwordHash("hash")
                .role(role)
                .status(status)
                .isActive(active)
                .build();
        account.setId(id);
        return account;
    }

    @Test
    void meReturnsEmployeeProfileForEmployee() throws Exception {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(42L, "employee01", AccountRole.EMPLOYEE);
        when(jwtService.parseAccessToken("valid-access-token")).thenReturn(principal);
        when(accountRepository.findById(42L)).thenReturn(Optional.of(account(42L, "employee01", AccountRole.EMPLOYEE, AccountStatus.ACTIVE, true)));
        when(authService.me(principal)).thenReturn(new MeResponse(
                new AccountResponse(42L, "employee01", AccountRole.EMPLOYEE),
                new EmployeeResponse(7L, "NV007", "Nguyen Van A", "a@example.com", "0900000007", "ACTIVE")));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(42))
                .andExpect(jsonPath("$.employee.id").value(7))
                .andExpect(jsonPath("$.employee.employeeCode").value("NV007"))
                .andExpect(jsonPath("$.employee.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.employee.email").value("a@example.com"))
                .andExpect(jsonPath("$.employee.phone").value("0900000007"))
                .andExpect(jsonPath("$.employee.status").value("ACTIVE"));
        verify(authService).me(principal);
    }
}

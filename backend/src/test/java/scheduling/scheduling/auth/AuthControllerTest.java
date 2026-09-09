package scheduling.scheduling.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import scheduling.scheduling.auth.dto.LoginRequest;
import scheduling.scheduling.auth.dto.LoginStateResponse;
import scheduling.scheduling.auth.dto.MeResponse;
import scheduling.scheduling.auth.dto.VerifyActivationOtpRequest;
import scheduling.scheduling.entity.AccountRole;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

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

    private static AuthResponse authResponse(String accessToken, String username) {
        return new AuthResponse(
                accessToken,
                "Bearer",
                900,
                new AccountResponse(42L, username, AccountRole.EMPLOYEE));
    }
}

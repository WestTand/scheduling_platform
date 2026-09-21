# Self-Service Resend OTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let pending users resend their own activation OTP from the activation page using username and temporary password.

**Architecture:** Add a public backend auth endpoint that verifies username, pending activation state, active flag, and temporary password before delegating to the existing OTP creation service. Wire the frontend resend button to this endpoint in backend mode while preserving the existing mock and manager resend behavior.

**Tech Stack:** Spring Boot 4, Spring Security, JUnit/MockMvc, Mockito, React, TypeScript, Vite.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/auth/dto/ResendActivationOtpRequest.java`: request DTO for self-service resend.
- Modify `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`: expose `POST /api/auth/activation/resend`.
- Modify `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`: implement resend validation and OTP creation.
- Modify `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`: permit the new public endpoint.
- Modify `backend/src/main/java/scheduling/scheduling/auth/JwtAuthenticationFilter.java`: skip JWT parsing for the new public endpoint.
- Modify `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`: controller/security tests for public resend.
- Create or modify service-level backend tests if an existing `AuthServiceTest` is present; otherwise use controller tests plus direct service tests in `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`.
- Modify `frontend/src/services/backendAuthService.ts`: add backend client method.
- Modify `frontend/src/services/authService.ts`: add backend-aware resend lifecycle method.
- Modify `frontend/src/pages/auth/AccountActivationPage.tsx`: call self-service resend in backend mode.

### Task 1: Backend Request DTO and Controller Contract

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/ResendActivationOtpRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Add failing controller test for successful resend**

Add imports to `AuthControllerTest.java`:

```java
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
```

Add this test after `verifyActivationOtpReturnsPasswordChangeRequiredWithoutRefreshCookie`:

```java
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
```

- [ ] **Step 2: Add failing controller test for validation**

Add this test next to the successful resend test:

```java
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
```

- [ ] **Step 3: Run controller tests to verify failure**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest test
```

Expected: compilation fails because `ResendActivationOtpRequest` and `AuthService.resendActivationOtp` do not exist, or test fails with `404` if the DTO/method was added earlier.

- [ ] **Step 4: Create request DTO**

Create `backend/src/main/java/scheduling/scheduling/auth/dto/ResendActivationOtpRequest.java`:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendActivationOtpRequest(
        @NotBlank String username,
        @NotBlank String temporaryPassword) {
}
```

- [ ] **Step 5: Add controller endpoint**

Modify imports in `AuthController.java`:

```java
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
```

Add this endpoint after `verifyActivationOtp`:

```java
    @PostMapping("/activation/resend")
    ResponseEntity<Void> resendActivationOtp(@Valid @RequestBody ResendActivationOtpRequest request) {
        authService.resendActivationOtp(request);
        return ResponseEntity.noContent().build();
    }
```

Add a temporary no-op method to `AuthService.java` so this task compiles. It will be replaced in Task 2:

```java
    @Transactional
    public void resendActivationOtp(ResendActivationOtpRequest request) {
    }
```

Add this import to `AuthService.java`:

```java
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
```

- [ ] **Step 6: Run controller tests to verify pass**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest test
```

Expected: `AuthControllerTest` passes.

- [ ] **Step 7: Commit controller contract**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/auth/dto/ResendActivationOtpRequest.java backend/src/main/java/scheduling/scheduling/auth/AuthController.java backend/src/main/java/scheduling/scheduling/auth/AuthService.java backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java
git commit -m "feat: add activation otp resend endpoint contract"
```

### Task 2: Backend Resend Validation Logic

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`

- [ ] **Step 1: Check for existing service test**

Run:

```powershell
if (Test-Path -LiteralPath "backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java") { "EXISTS" } else { "MISSING" }
```

Expected: `EXISTS` or `MISSING`. If missing, create the file in Step 2.

- [ ] **Step 2: Add service tests**

If the file is missing, create `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java` with:

```java
package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.auth.dto.ResendActivationOtpRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountActivationOtpService activationOtpService;

    private AuthService authService;

    private Account account;

    private ResendActivationOtpRequest request;

    @BeforeEach
    void setUp() {
        authService = new AuthService(accountRepository, passwordEncoder, jwtService, refreshTokenService, activationOtpService);
        account = new Account();
        account.setId(42L);
        account.setUsername("employee01");
        account.setPasswordHash("hashed-password");
        account.setIsActive(true);
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        request = new ResendActivationOtpRequest("employee01", "secret123");
    }

    @Test
    void resendActivationOtpCreatesOtpForPendingAccountWithMatchingTemporaryPassword() {
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(true);

        authService.resendActivationOtp(request);

        verify(activationOtpService).createActivationOtp(account);
    }

    @Test
    void resendActivationOtpRejectsWrongTemporaryPassword() {
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.resendActivationOtp(request))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(activationOtpService, never()).createActivationOtp(account);
    }

    @Test
    void resendActivationOtpRejectsNonPendingAccount() {
        account.setStatus(AccountStatus.ACTIVE);
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.resendActivationOtp(request))
                .isInstanceOf(InvalidAccountStateException.class);

        verify(activationOtpService, never()).createActivationOtp(account);
    }

    @Test
    void resendActivationOtpRejectsUnknownUsername() {
        when(accountRepository.findByUsername("employee01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendActivationOtp(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
```

If the file exists, add the same four tests and matching mocks/setup to the existing class instead of duplicating the class.

- [ ] **Step 3: Run service tests to verify failure**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthServiceTest test
```

Expected: at least the success test fails because Task 1 added a no-op `resendActivationOtp` implementation.

- [ ] **Step 4: Implement resend validation**

Replace the no-op method in `AuthService.java` with:

```java
    @Transactional
    public void resendActivationOtp(ResendActivationOtpRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(AuthenticationFailedException::new);
        if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new InvalidAccountStateException();
        }
        if (!passwordEncoder.matches(request.temporaryPassword(), account.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }

        accountActivationOtpService.createActivationOtp(account);
    }
```

Use the existing OTP service field name in `AuthService.java`. If the field is named `otpService` instead of `accountActivationOtpService`, call `otpService.createActivationOtp(account)`.

- [ ] **Step 5: Run service tests to verify pass**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthServiceTest test
```

Expected: all `AuthServiceTest` tests pass.

- [ ] **Step 6: Run controller tests again**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest test
```

Expected: all `AuthControllerTest` tests pass.

- [ ] **Step 7: Commit backend validation**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/auth/AuthService.java backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java
git commit -m "feat: validate self-service activation otp resend"
```

### Task 3: Public Security Allowlist

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/JwtAuthenticationFilter.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Add test proving invalid bearer token is ignored**

Add this test near `changeRequiredPasswordIgnoresInvalidBearerToken` in `AuthControllerTest.java`:

```java
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
                .andExpect(status().isNoContent());

        verify(authService).resendActivationOtp(any(ResendActivationOtpRequest.class));
    }
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest#resendActivationOtpIgnoresInvalidBearerToken test
```

Expected: fails with unauthorized if the filter does not skip the new endpoint.

- [ ] **Step 3: Permit endpoint in Spring Security**

In `SecurityConfig.java`, add the endpoint to the existing public auth matcher list:

```java
"/api/auth/activation/resend",
```

Keep it next to:

```java
"/api/auth/activation/verify-otp",
```

- [ ] **Step 4: Skip JWT parsing for endpoint**

In `JwtAuthenticationFilter.java`, add the endpoint to the existing public paths list:

```java
"/api/auth/activation/resend",
```

Keep it next to:

```java
"/api/auth/activation/verify-otp",
```

- [ ] **Step 5: Run security test to verify pass**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest#resendActivationOtpIgnoresInvalidBearerToken test
```

Expected: test passes.

- [ ] **Step 6: Run full auth controller tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest test
```

Expected: all `AuthControllerTest` tests pass.

- [ ] **Step 7: Commit security allowlist**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java backend/src/main/java/scheduling/scheduling/auth/JwtAuthenticationFilter.java backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java
git commit -m "fix: allow public activation otp resend"
```

### Task 4: Frontend Service Wiring

**Files:**
- Modify: `frontend/src/services/backendAuthService.ts`
- Modify: `frontend/src/services/authService.ts`

- [ ] **Step 1: Add backend client method**

In `frontend/src/services/backendAuthService.ts`, add this method after `verifyActivationOtp`:

```ts
  async resendActivationOtp(username: string, temporaryPassword: string): Promise<void> {
    await requestVoid('/api/auth/activation/resend', {
      method: 'POST',
      auth: false,
      body: {
        username: username.trim(),
        temporaryPassword,
      },
    })
  },
```

- [ ] **Step 2: Add backend-aware auth service method**

In `frontend/src/services/authService.ts`, add this method after `verifyActivationOtp`:

```ts
  async resendActivationOtp(accountId: string | number, username: string, temporaryPassword: string): Promise<void> {
    if (apiConfig.useMockApi) {
      await accountService.resendActivationOtp(String(accountId))
      return
    }

    if (!username.trim() || !temporaryPassword) {
      throw new Error('Vui lòng nhập tên đăng nhập và mật khẩu tạm thời để gửi lại OTP.')
    }

    await backendAuthService.resendActivationOtp(username, temporaryPassword)
  },
```

- [ ] **Step 3: Run frontend typecheck/build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`

Expected: build passes.

- [ ] **Step 4: Commit frontend service wiring**

Run:

```powershell
git add frontend/src/services/backendAuthService.ts frontend/src/services/authService.ts
git commit -m "feat: add frontend activation otp resend service"
```

### Task 5: Frontend Activation Page Resend Flow

**Files:**
- Modify: `frontend/src/pages/auth/AccountActivationPage.tsx`

- [ ] **Step 1: Replace backend contact-manager branch**

In `handleResendOtp`, replace:

```ts
      if (!apiConfig.useMockApi) {
        setSuccessMessage('Vui lòng liên hệ quản lý để gửi lại mã OTP.')
        setCountdown(activationDelaySeconds)
        return
      }

      await accountService.resendActivationOtp(accountId)
      setCountdown(activationDelaySeconds)
      setOtp('')
      setSuccessMessage('Mã OTP mới đã được gửi lại. OTP mock hiện đang sử dụng: 123456.')
```

with:

```ts
      await authService.resendActivationOtp(accountId, username, temporaryPassword)
      setCountdown(activationDelaySeconds)
      setOtp('')
      setSuccessMessage(apiConfig.useMockApi ? `Mã OTP mới đã được gửi lại. OTP mock hiện đang sử dụng: ${MOCK_OTP}.` : 'Mã OTP mới đã được gửi lại. Vui lòng kiểm tra email của bạn.')
```

- [ ] **Step 2: Remove unused direct account service resend dependency if needed**

Keep this import because `handleRequestOtp` still uses `accountService` and mock message uses `MOCK_OTP`:

```ts
import { accountService, MOCK_OTP } from '@/services/accountService'
```

No import removal is needed if TypeScript reports no unused imports.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`

Expected: build passes.

- [ ] **Step 4: Commit activation page flow**

Run:

```powershell
git add frontend/src/pages/auth/AccountActivationPage.tsx
git commit -m "feat: enable user activation otp resend"
```

### Task 6: End-to-End Verification

**Files:**
- No source files expected unless verification exposes a bug.

- [ ] **Step 1: Run backend auth tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AuthControllerTest,AuthServiceTest test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`

Expected: build passes.

- [ ] **Step 3: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no errors. Line-ending warnings are acceptable only if they already existed and do not indicate whitespace errors.

- [ ] **Step 4: Optionally verify live endpoint**

If Docker backend is running with the latest code, run:

```powershell
Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/api/auth/activation/resend' -ContentType 'application/json' -Body '{"username":"phat143","temporaryPassword":"1111@"}' -UseBasicParsing -TimeoutSec 60
```

Expected: HTTP `204 No Content`. If the OTP mail service is configured, a new OTP email is sent.

- [ ] **Step 5: Commit verification docs only if changed**

If no source changes were needed during verification, do not create a commit. If a bug was fixed during verification, commit only the files changed for that fix with a specific message.

## Self-Review Notes

- Spec coverage: backend endpoint, validation, public security config, frontend service, activation page behavior, and verification are covered.
- Placeholder scan: no `TBD`, `TODO`, or undefined deferred work remains.
- Type consistency: request fields are consistently `username` and `temporaryPassword`; frontend method names are consistently `resendActivationOtp`.

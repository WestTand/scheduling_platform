# Required Password Change Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/auth/password/change-required` so `FORCE_PASSWORD_CHANGE` accounts can set a new password, become `ACTIVE`, and receive normal login tokens.

**Architecture:** Keep lifecycle validation and password hashing in `AuthService`, matching existing login/refresh behavior. `AuthController` exposes a public endpoint that returns the same `AuthResponse` body and refresh cookie shape as successful login.

**Tech Stack:** Spring Boot 4.1, Spring MVC, Jakarta Validation, Spring Security, BCrypt `PasswordEncoder`, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/RequiredPasswordChangeRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidPasswordChangeStateException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

## Behavior

- Request body: `{ "accountId": 42, "newPassword": "new-secure-password" }`.
- Endpoint is public because `FORCE_PASSWORD_CHANGE` accounts do not have JWTs.
- Only accounts with `isActive = true` and `status = FORCE_PASSWORD_CHANGE` can change password through this endpoint.
- On success, encode `newPassword`, set account status to `ACTIVE`, save account, create refresh token, and return normal `AuthResponse` plus HttpOnly refresh cookie.
- Missing account, inactive account, or wrong lifecycle state returns `401 INVALID_PASSWORD_CHANGE_STATE`.
- No old-password check in this slice; the user reached this state through valid login credentials or valid activation OTP.

### Task 1: AuthService Password Change Behavior

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/RequiredPasswordChangeRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidPasswordChangeStateException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add imports to `AuthServiceTest`:

```java
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
```

Add tests to `AuthServiceTest`:

```java
@Test
void changeRequiredPasswordActivatesAccountAndReturnsTokens() {
    Account account = account(42L, "employee01", "old-hash", true, null);
    account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
    RequiredPasswordChangeRequest request = new RequiredPasswordChangeRequest(42L, "new-password");
    RefreshTokenResult refreshToken = refreshToken(account);
    when(accountRepository.findById(42L)).thenReturn(Optional.of(account));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    when(refreshTokenService.create(account)).thenReturn(refreshToken);
    when(jwtService.createAccessToken(42L, "employee01", AccountRole.EMPLOYEE)).thenReturn("access-token");
    when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

    LoginResult result = service.changeRequiredPassword(request);

    assertThat(account.getPasswordHash()).isEqualTo("new-hash");
    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    verify(accountRepository).save(account);
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.response().accessToken()).isEqualTo("access-token");
    assertThat(result.response().account().id()).isEqualTo(42L);
}

@Test
void changeRequiredPasswordRejectsMissingAccount() {
    when(accountRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "new-password")))
            .isInstanceOf(InvalidPasswordChangeStateException.class)
            .hasMessage("Invalid password change state");
}

@Test
void changeRequiredPasswordRejectsInactiveAccount() {
    Account account = account(42L, "employee01", "old-hash", false, null);
    account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
    when(accountRepository.findById(42L)).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "new-password")))
            .isInstanceOf(InvalidPasswordChangeStateException.class);
}

@Test
void changeRequiredPasswordRejectsWrongStatus() {
    Account account = account(42L, "employee01", "old-hash", true, null);
    account.setStatus(AccountStatus.PENDING_ACTIVATION);
    when(accountRepository.findById(42L)).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.changeRequiredPassword(new RequiredPasswordChangeRequest(42L, "new-password")))
            .isInstanceOf(InvalidPasswordChangeStateException.class);
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./mvnw.cmd "-Dtest=AuthServiceTest" test`

Expected: FAIL because request DTO, exception, and `changeRequiredPassword` do not exist.

- [ ] **Step 3: Add request DTO**

Create `RequiredPasswordChangeRequest.java`:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredPasswordChangeRequest(
        @NotNull Long accountId,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}
```

- [ ] **Step 4: Add exception**

Create `InvalidPasswordChangeStateException.java`:

```java
package scheduling.scheduling.auth;

public class InvalidPasswordChangeStateException extends RuntimeException {

    public InvalidPasswordChangeStateException() {
        super("Invalid password change state");
    }
}
```

- [ ] **Step 5: Implement service method**

Add import to `AuthService`:

```java
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
```

Add method to `AuthService`:

```java
@Transactional
public LoginResult changeRequiredPassword(RequiredPasswordChangeRequest request) {
    Account account = accountRepository.findById(request.accountId())
            .orElseThrow(InvalidPasswordChangeStateException::new);
    if (!Boolean.TRUE.equals(account.getIsActive()) || account.getStatus() != AccountStatus.FORCE_PASSWORD_CHANGE) {
        throw new InvalidPasswordChangeStateException();
    }

    account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    account.setStatus(AccountStatus.ACTIVE);
    accountRepository.save(account);
    return loginResult(account, refreshTokenService.create(account));
}
```

- [ ] **Step 6: Run focused service tests**

Run: `./mvnw.cmd "-Dtest=AuthServiceTest" test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/auth/AuthService.java backend/src/main/java/scheduling/scheduling/auth/InvalidPasswordChangeStateException.java backend/src/main/java/scheduling/scheduling/auth/dto/RequiredPasswordChangeRequest.java backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java && git commit -m "feat: change required password"`

### Task 2: Password Change Endpoint

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add import to `AuthControllerTest`:

```java
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
```

Add tests to `AuthControllerTest`:

```java
@Test
void changeRequiredPasswordReturnsTokensAndRefreshCookie() throws Exception {
    when(authService.changeRequiredPassword(any(RequiredPasswordChangeRequest.class)))
            .thenReturn(new LoginResult(authResponse("access-token", "employee01"), "refresh-token"));

    mockMvc.perform(post("/api/auth/password/change-required")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "accountId": 42,
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
                              "newPassword": "new-password"
                            }
                            """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_CHANGE_STATE"))
            .andExpect(jsonPath("$.message").value("Invalid password change state"));
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./mvnw.cmd "-Dtest=AuthControllerTest" test`

Expected: FAIL because endpoint, security permit rule, and exception mapping are not implemented.

- [ ] **Step 3: Add endpoint**

Add imports to `AuthController`:

```java
import scheduling.scheduling.auth.dto.RequiredPasswordChangeRequest;
```

Add method to `AuthController`:

```java
@PostMapping("/password/change-required")
ResponseEntity<AuthResponse> changeRequiredPassword(@Valid @RequestBody RequiredPasswordChangeRequest request) {
    LoginResult result = authService.changeRequiredPassword(request);
    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
            .body(result.response());
}
```

- [ ] **Step 4: Add public security rule**

Add `"/api/auth/password/change-required"` to `SecurityConfig` public request matchers.

- [ ] **Step 5: Add exception mapping**

Add to `AuthExceptionHandler`:

```java
@ExceptionHandler(InvalidPasswordChangeStateException.class)
ResponseEntity<ErrorResponse> handleInvalidPasswordChangeState(InvalidPasswordChangeStateException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_PASSWORD_CHANGE_STATE", ex.getMessage()));
}
```

- [ ] **Step 6: Run focused controller tests**

Run: `./mvnw.cmd "-Dtest=AuthControllerTest" test`

Expected: PASS.

- [ ] **Step 7: Run full verification and safety checks**

Run: `./mvnw.cmd clean test`

Expected: PASS.

Run: repo-wide search for forbidden default-password literals.

Expected: no matches.

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 8: Commit and push**

Run: `git add backend/src/main/java/scheduling/scheduling/auth backend/src/test/java/scheduling/scheduling/auth && git commit -m "feat: add required password change endpoint" && git push origin main`

## Self-Review

- Spec coverage: implements public required-password-change endpoint, lifecycle validation, BCrypt password update, transition to `ACTIVE`, normal token/cookie response, and invalid state error.
- Scope gaps intentionally deferred: manager password reset, resend OTP, refresh/me lifecycle blocking for non-active accounts.
- Placeholder scan: no placeholder implementation steps remain.
- Type consistency: DTO, exception, service method, endpoint path, and error code are consistent across tasks.

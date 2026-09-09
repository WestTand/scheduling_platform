# Verify Activation OTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/auth/activation/verify-otp` so employees can verify the activation OTP and move from `PENDING_ACTIVATION` to `FORCE_PASSWORD_CHANGE`.

**Architecture:** Keep OTP verification inside `AccountActivationOtpService` because it owns OTP persistence, hashing, expiry, and one-time-use rules. `AuthService` exposes a narrow activation method for `AuthController`, matching the existing login/refresh/me controller pattern.

**Tech Stack:** Spring Boot 4.1, Spring MVC, Jakarta Validation, Spring Data JPA, BCrypt `PasswordEncoder`, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/VerifyActivationOtpRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/ActivationStateResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidActivationOtpException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/AccountActivationOtpRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AccountActivationOtpService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AccountActivationOtpServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

## Behavior

- Request body: `{ "accountId": 123, "otp": "123456" }`
- Only accounts in `PENDING_ACTIVATION` can verify activation OTP.
- Verification checks the newest unused OTP for that account.
- OTP must not be expired.
- OTP is compared with `passwordEncoder.matches(rawOtp, otpHash)`.
- On success, set `otp.usedAt = now`, set `account.status = FORCE_PASSWORD_CHANGE`, save the OTP, and return `{ "status": "PASSWORD_CHANGE_REQUIRED", "accountId": 123, "username": "employee01" }`.
- Do not issue access tokens or refresh cookies from this endpoint.
- On missing account, non-pending account, missing OTP record, expired OTP, used OTP, or wrong OTP, return `401` with code `INVALID_ACTIVATION_OTP`.

### Task 1: Repository Query For Latest Unused OTP

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/AccountActivationOtpRepository.java`

- [ ] **Step 1: Add repository query method**

```java
package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.AccountActivationOtp;

public interface AccountActivationOtpRepository extends JpaRepository<AccountActivationOtp, Long> {

    Optional<AccountActivationOtp> findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(Long accountId);
}
```

- [ ] **Step 2: Run focused entity/repository compile check**

Run: `./mvnw.cmd "-Dtest=EntityMappingTest" test`

Expected: PASS.

- [ ] **Step 3: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/repository/AccountActivationOtpRepository.java && git commit -m "feat: query activation otp by account"`

### Task 2: OTP Verification Service

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidActivationOtpException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AccountActivationOtpService.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AccountActivationOtpServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add these tests to `AccountActivationOtpServiceTest`:

```java
@Test
void verifyActivationOtpMarksOtpUsedAndMovesAccountToForcePasswordChange() {
    Account account = account();
    account.setStatus(AccountStatus.PENDING_ACTIVATION);
    AccountActivationOtp otp = AccountActivationOtp.builder()
            .account(account)
            .otpHash("otp-hash")
            .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
            .build();
    when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
            .thenReturn(Optional.of(otp));
    when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);

    Account result = service.verifyActivationOtp(20L, "123456");

    assertThat(result).isSameAs(account);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.FORCE_PASSWORD_CHANGE);
    assertThat(otp.getUsedAt()).isEqualTo(LocalDateTime.of(2026, 9, 9, 0, 0));
    verify(otpRepository).save(otp);
}

@Test
void verifyActivationOtpRejectsWrongOtp() {
    Account account = account();
    account.setStatus(AccountStatus.PENDING_ACTIVATION);
    AccountActivationOtp otp = AccountActivationOtp.builder()
            .account(account)
            .otpHash("otp-hash")
            .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
            .build();
    when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
            .thenReturn(Optional.of(otp));
    when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

    assertThatThrownBy(() -> service.verifyActivationOtp(20L, "000000"))
            .isInstanceOf(InvalidActivationOtpException.class);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
    assertThat(otp.getUsedAt()).isNull();
}

@Test
void verifyActivationOtpRejectsExpiredOtp() {
    Account account = account();
    account.setStatus(AccountStatus.PENDING_ACTIVATION);
    AccountActivationOtp otp = AccountActivationOtp.builder()
            .account(account)
            .otpHash("otp-hash")
            .expiresAt(LocalDateTime.of(2026, 9, 8, 23, 59))
            .build();
    when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
            .thenReturn(Optional.of(otp));

    assertThatThrownBy(() -> service.verifyActivationOtp(20L, "123456"))
            .isInstanceOf(InvalidActivationOtpException.class);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
    assertThat(otp.getUsedAt()).isNull();
}

@Test
void verifyActivationOtpRejectsNonPendingAccount() {
    Account account = account();
    account.setStatus(AccountStatus.ACTIVE);
    AccountActivationOtp otp = AccountActivationOtp.builder()
            .account(account)
            .otpHash("otp-hash")
            .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
            .build();
    when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
            .thenReturn(Optional.of(otp));

    assertThatThrownBy(() -> service.verifyActivationOtp(20L, "123456"))
            .isInstanceOf(InvalidActivationOtpException.class);
}
```

Also add imports:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import scheduling.scheduling.entity.AccountStatus;
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./mvnw.cmd "-Dtest=AccountActivationOtpServiceTest" test`

Expected: FAIL because `verifyActivationOtp` and the repository method do not exist yet if Task 1 has not been executed, or because service behavior is not implemented yet.

- [ ] **Step 3: Add exception**

Create `InvalidActivationOtpException.java`:

```java
package scheduling.scheduling.auth;

public class InvalidActivationOtpException extends RuntimeException {

    public InvalidActivationOtpException() {
        super("Invalid activation OTP");
    }
}
```

- [ ] **Step 4: Implement service method**

Add to `AccountActivationOtpService`:

```java
@Transactional
public Account verifyActivationOtp(Long accountId, String rawOtp) {
    LocalDateTime now = LocalDateTime.now(clock);
    AccountActivationOtp otp = otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(accountId)
            .orElseThrow(InvalidActivationOtpException::new);
    Account account = otp.getAccount();
    if (account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
        throw new InvalidActivationOtpException();
    }
    if (!otp.getExpiresAt().isAfter(now)) {
        throw new InvalidActivationOtpException();
    }
    if (!passwordEncoder.matches(rawOtp, otp.getOtpHash())) {
        throw new InvalidActivationOtpException();
    }

    otp.setUsedAt(now);
    account.setStatus(AccountStatus.FORCE_PASSWORD_CHANGE);
    otpRepository.save(otp);
    return account;
}
```

Also import `AccountStatus`.

- [ ] **Step 5: Run focused tests**

Run: `./mvnw.cmd "-Dtest=AccountActivationOtpServiceTest" test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/auth/InvalidActivationOtpException.java backend/src/main/java/scheduling/scheduling/auth/AccountActivationOtpService.java backend/src/test/java/scheduling/scheduling/auth/AccountActivationOtpServiceTest.java && git commit -m "feat: verify activation otp"`

### Task 3: Auth Endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/VerifyActivationOtpRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/ActivationStateResponse.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add to `AuthControllerTest`:

```java
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
```

Add imports:

```java
import scheduling.scheduling.auth.dto.ActivationStateResponse;
import scheduling.scheduling.auth.dto.VerifyActivationOtpRequest;
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./mvnw.cmd "-Dtest=AuthControllerTest" test`

Expected: FAIL because DTOs, service method, endpoint, and exception mapping are not implemented.

- [ ] **Step 3: Add DTOs**

Create `VerifyActivationOtpRequest.java`:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyActivationOtpRequest(
        @NotNull Long accountId,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
```

Create `ActivationStateResponse.java`:

```java
package scheduling.scheduling.auth.dto;

public record ActivationStateResponse(String status, Long accountId, String username) {
}
```

- [ ] **Step 4: Add service wrapper**

Inject `AccountActivationOtpService` into `AuthService` constructor and add:

```java
@Transactional
public ActivationStateResponse verifyActivationOtp(VerifyActivationOtpRequest request) {
    Account account = accountActivationOtpService.verifyActivationOtp(request.accountId(), request.otp());
    return new ActivationStateResponse("PASSWORD_CHANGE_REQUIRED", account.getId(), account.getUsername());
}
```

Also import `ActivationStateResponse` and `VerifyActivationOtpRequest`.

- [ ] **Step 5: Add endpoint**

Add to `AuthController`:

```java
@PostMapping("/activation/verify-otp")
ResponseEntity<ActivationStateResponse> verifyActivationOtp(@Valid @RequestBody VerifyActivationOtpRequest request) {
    return ResponseEntity.ok(authService.verifyActivationOtp(request));
}
```

Also import `ActivationStateResponse` and `VerifyActivationOtpRequest`.

- [ ] **Step 6: Add exception mapping**

Add to `AuthExceptionHandler`:

```java
@ExceptionHandler(InvalidActivationOtpException.class)
ResponseEntity<ErrorResponse> handleInvalidActivationOtp(InvalidActivationOtpException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_ACTIVATION_OTP", ex.getMessage()));
}
```

- [ ] **Step 7: Run focused controller tests**

Run: `./mvnw.cmd "-Dtest=AuthControllerTest" test`

Expected: PASS.

- [ ] **Step 8: Run full verification and safety checks**

Run: `./mvnw.cmd clean test`

Expected: PASS.

Run: repo-wide search for forbidden default-password literals.

Expected: no matches.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 9: Commit and push**

Run: `git add backend/src/main/java/scheduling/scheduling/auth backend/src/main/java/scheduling/scheduling/repository/AccountActivationOtpRepository.java backend/src/test/java/scheduling/scheduling/auth && git commit -m "feat: add activation otp verification" && git push origin main`

## Self-Review

- Spec coverage: implements public verify OTP endpoint, valid OTP status transition, one-time use, expiry, no token/cookie issuance, and invalid OTP rejection.
- Scope gaps intentionally deferred: resend OTP, required password change endpoint, manager reset, refresh/me status blocking.
- Placeholder scan: no placeholder implementation steps remain.
- Type consistency: DTO names, service methods, endpoint path, and exception code are consistent across tasks.

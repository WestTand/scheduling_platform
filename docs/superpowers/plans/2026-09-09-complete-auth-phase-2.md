# Complete Auth Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish remaining Phase 2 Auth & Security work: token lifecycle hardening, manager OTP resend, manager password reset, and refresh-token revocation on reset.

**Architecture:** Keep employee activation operations in `ManagerAccountService` and token/account lifecycle checks in `AuthService`. Add only the repository/service methods needed by the manager reset flow, and preserve existing controller/test patterns.

**Tech Stack:** Spring Boot 4.1, Spring MVC, Spring Security method authorization, Spring Data JPA, BCrypt `PasswordEncoder`, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/AccountNotFoundException.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidAccountStateException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/RefreshTokenService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/RefreshTokenRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/RefreshTokenServiceTest.java`

## Task 1: Harden Refresh And Me Lifecycle

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`

- [ ] Add failing tests that `refresh` rejects `FORCE_PASSWORD_CHANGE` and `/me` rejects `PENDING_ACTIVATION` with `InvalidTokenException`.
- [ ] Implement a private `requireUsableAccount(Account account, String message)` that requires `isActive=true` and `status=ACTIVE`.
- [ ] Use it from `refresh` and `me`.
- [ ] Run `./mvnw.cmd "-Dtest=AuthServiceTest" test` and confirm pass.
- [ ] Commit `feat: enforce active account tokens`.

## Task 2: Revoke Refresh Tokens By Account

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/RefreshTokenRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/RefreshTokenService.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/RefreshTokenServiceTest.java`

- [ ] Add failing test that `revokeAllForAccount(accountId)` marks all unrevoked account tokens revoked.
- [ ] Add repository method `List<RefreshToken> findByAccountIdAndRevokedAtIsNull(Long accountId)`.
- [ ] Add `RefreshTokenService.revokeAllForAccount(Long accountId)`.
- [ ] Run `./mvnw.cmd "-Dtest=RefreshTokenServiceTest" test` and confirm pass.
- [ ] Commit `feat: revoke refresh tokens by account`.

## Task 3: Manager Resend OTP And Reset Password

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/AccountNotFoundException.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/InvalidAccountStateException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountControllerTest.java`

- [ ] Add failing service tests for resend OTP success, resend missing account, resend wrong state, reset success, reset missing account, reset non-employee/wrong state.
- [ ] Add failing controller tests for `POST /api/manager/accounts/{accountId}/activation/resend`, `POST /api/manager/accounts/{accountId}/password/reset`, and employee forbidden access.
- [ ] Inject `RefreshTokenService` into `ManagerAccountService`.
- [ ] Implement `resendActivationOtp(Long accountId)` for active `PENDING_ACTIVATION` accounts.
- [ ] Implement `resetPassword(Long accountId)` for active `EMPLOYEE` accounts: encode default password, set `FORCE_PASSWORD_CHANGE`, save, revoke all tokens.
- [ ] Add controller endpoints with `@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")` and `204 No Content` responses.
- [ ] Map `AccountNotFoundException` to `404 ACCOUNT_NOT_FOUND` and `InvalidAccountStateException` to `409 INVALID_ACCOUNT_STATE`.
- [ ] Run `./mvnw.cmd "-Dtest=ManagerAccountServiceTest,ManagerAccountControllerTest" test` and confirm pass.
- [ ] Commit `feat: add manager activation resend and password reset`.

## Task 4: Final Verification

- [ ] Run `./mvnw.cmd clean test` from `backend` and confirm pass.
- [ ] Run repo-wide search for forbidden default-password literals and confirm no matches.
- [ ] Run `git diff --check` and confirm no whitespace errors.
- [ ] Run `git status --short --branch` and review staged/untracked state.
- [ ] Push `main`.

## Self-Review

- Spec coverage: covers all remaining Phase 2 tasks identified by project plan and current implementation gaps.
- Placeholder scan: no placeholder tasks remain; each task states exact files, behavior, commands, and commit messages.
- Type consistency: exception names, endpoint paths, and service method names are consistent across tasks.

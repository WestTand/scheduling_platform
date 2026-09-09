# Auth Login Lifecycle States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make login return lifecycle state responses for `PENDING_ACTIVATION` and `FORCE_PASSWORD_CHANGE` accounts without issuing tokens.

**Architecture:** Extend the existing login result shape so `AuthController` can return either the current token response or a lifecycle state response. Keep credential checking in `AuthService`, and keep refresh-cookie creation only for successful active logins.

**Tech Stack:** Spring Boot 4.1, Spring MVC, Spring Security, JUnit 5, Mockito, AssertJ, MockMvc.

---

### Task 1: Add Login State DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/dto/LoginStateResponse.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/LoginResult.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add tests in `AuthServiceTest` for `PENDING_ACTIVATION` and `FORCE_PASSWORD_CHANGE` login states.

- [ ] **Step 2: Verify RED**

Run `./mvnw.cmd "-Dtest=AuthServiceTest" test`; expect compile failure because `LoginStateResponse` and lifecycle login result helpers do not exist.

- [ ] **Step 3: Implement minimal DTO/result support**

Create `LoginStateResponse` with `status`, `accountId`, and `username`. Update `LoginResult` to hold either `AuthResponse` plus refresh token, or `LoginStateResponse` with no refresh token.

- [ ] **Step 4: Verify GREEN**

Run `./mvnw.cmd "-Dtest=AuthServiceTest" test`; expect all `AuthServiceTest` tests pass.

### Task 2: Return Lifecycle State From Controller

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add tests that `POST /api/auth/login` returns `OTP_REQUIRED` or `PASSWORD_CHANGE_REQUIRED` without `Set-Cookie`.

- [ ] **Step 2: Verify RED**

Run `./mvnw.cmd "-Dtest=AuthControllerTest" test`; expect failures because controller always writes token response/cookie.

- [ ] **Step 3: Implement minimal controller branching**

If `LoginResult` has state response, return it directly with `200 OK` and no refresh cookie. If it has auth response, keep current behavior.

- [ ] **Step 4: Verify focused and full GREEN**

Run `./mvnw.cmd "-Dtest=AuthServiceTest,AuthControllerTest" test`, then `./mvnw.cmd clean test`.

- [ ] **Step 5: Commit and push**

Run `git diff --check`, commit with `feat: return login lifecycle states`, then push `main`.

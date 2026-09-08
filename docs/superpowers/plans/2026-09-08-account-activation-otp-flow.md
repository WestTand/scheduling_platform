# Account Activation OTP Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor auth from public employee self-registration to manager-created accounts with email OTP activation and forced password change.

**Architecture:** Keep JWT/refresh-token infrastructure, but add account lifecycle state and OTP activation state around it. Manager account APIs create/reset employee accounts; public auth APIs only handle login, OTP verification, forced password change, refresh, logout, and me.

**Tech Stack:** Spring Boot 4.1.1, Spring Security, Spring Data JPA, PostgreSQL, BCrypt, JJWT, JUnit 5, AssertJ, MockMvc.

---

## Phase A - Account Lifecycle Model

- [ ] Add enum `AccountStatus`: `PENDING_ACTIVATION`, `FORCE_PASSWORD_CHANGE`, `ACTIVE`, `INACTIVE`.
- [ ] Add `status` column to `Account` entity with default `PENDING_ACTIVATION` for manager-created employee accounts.
- [ ] Keep `isActive` as hard lock flag until database design is revised.
- [ ] Add tests proving default status and mapping.
- [ ] Update login/refresh/me checks to require `status == ACTIVE` and `isActive == true`.

## Phase B - OTP Persistence

- [ ] Add entity `AccountActivationOtp` with `otp_id`, `account_id`, `otp_hash`, `expires_at`, `used_at`, `created_at`.
- [ ] Add repository `AccountActivationOtpRepository`.
- [ ] Store only OTP hash, never raw OTP.
- [ ] Add method to validate unused and unexpired OTP.
- [ ] Add tests for expired OTP, used OTP, wrong OTP, and valid OTP.

## Phase C - Mail Abstraction

- [ ] Add `AccountMailService` interface.
- [ ] Add dev implementation that logs activation/reset messages when SMTP is not configured.
- [ ] Add config placeholders for SMTP/from address if real email is added later.
- [ ] Test that manager create/reset flow calls mail service with account email.

## Phase D - Manager Account APIs

- [ ] Remove public `POST /api/auth/register` from controller and security config.
- [ ] Add manager-only `POST /api/admin/accounts/employees` to create employee profile and account.
- [ ] Request includes employee code, full name, email, phone, username, and temporary password or generated temporary password strategy.
- [ ] Created account has role `EMPLOYEE`, `status=PENDING_ACTIVATION`, `isActive=true`.
- [ ] Generate OTP and send activation email.
- [ ] Add manager-only resend activation OTP endpoint.
- [ ] Add manager-only reset password endpoint.
- [ ] Reset password revokes existing refresh tokens and sets account to `FORCE_PASSWORD_CHANGE` or `PENDING_ACTIVATION` based on whether email must be reverified.
- [ ] Add controller/service tests for manager create, resend OTP, reset password, duplicate username/email/employee code.

## Phase E - Employee Activation APIs

- [ ] Add `POST /api/auth/activation/verify-otp`.
- [ ] Valid OTP marks OTP used and moves account to `FORCE_PASSWORD_CHANGE`.
- [ ] Invalid/expired/used OTP returns `401 INVALID_TOKEN` or `400 VALIDATION_FAILED` consistently.
- [ ] Add `POST /api/auth/password/change-required`.
- [ ] Password change requires username, temporary/current password, and new password.
- [ ] Successful forced password change stores BCrypt hash and sets `status=ACTIVE`.
- [ ] Normal login issues JWT only after account is `ACTIVE`.
- [ ] Add service/controller tests for first login needing OTP, OTP verification, forced password change, and successful login after activation.

## Phase F - Frontend Task Updates

- [ ] Remove public register page from frontend plan.
- [ ] Add manager create account form.
- [ ] Add OTP verification page.
- [ ] Add forced password change page.
- [ ] Add forgot password screen telling employee to contact manager.
- [ ] Add manager reset password/resend OTP actions.

## Phase G - Verification

- [ ] Run targeted auth tests: `./mvnw.cmd "-Dtest=AuthServiceTest,AuthControllerTest,EntityMappingTest" test`.
- [ ] Run full backend tests: `./mvnw.cmd test`.
- [ ] Run `git diff --check`.
- [ ] Push branch to GitHub.

## Notes

- The existing branch/auth implementation already has JWT, refresh token rotation, revoke, and security filter behavior. Reuse those pieces instead of rewriting them.
- The current public register DTO/service/controller tests must be replaced, not kept as backward compatibility.
- No public forgot-password endpoint should be implemented.

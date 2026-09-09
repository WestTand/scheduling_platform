# Create Activation OTP On Account Create Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate, store, and email an activation OTP whenever a manager/admin creates an employee account.

**Architecture:** Add a dedicated `AccountActivationOtp` entity/repository, an `AccountActivationOtpService` for generation and persistence, and an `AccountMailService` abstraction for delivery. `ManagerAccountService` remains responsible for account creation and delegates OTP/email behavior after account save.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, Spring Mail, BCrypt `PasswordEncoder`, JUnit 5, Mockito, AssertJ.

---

### Task 1: OTP Entity And Repository

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/entity/AccountActivationOtp.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/AccountActivationOtpRepository.java`
- Modify: `backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java`

- [ ] Write failing entity mapping test for `AccountActivationOtp` primary key override and required columns.
- [ ] Run `./mvnw.cmd "-Dtest=EntityMappingTest" test` and confirm failure.
- [ ] Add entity with `account`, `otpHash`, `expiresAt`, `usedAt`, `createdAt`.
- [ ] Add JPA repository.
- [ ] Run focused entity test and confirm pass.

### Task 2: OTP Service And Mail Abstraction

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/AccountActivationOtpService.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/AccountMailService.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/GmailAccountMailService.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/MailDeliveryException.java`
- Create: `backend/src/test/java/scheduling/scheduling/auth/AccountActivationOtpServiceTest.java`

- [ ] Write failing service test that generated OTP is 6 digits, stored hashed, expires after 10 minutes, and email receives the raw OTP.
- [ ] Run `./mvnw.cmd "-Dtest=AccountActivationOtpServiceTest" test` and confirm compile failure.
- [ ] Implement `AccountActivationOtpService.createActivationOtp(Account account)`.
- [ ] Implement `GmailAccountMailService` using `JavaMailSender`, `MAIL_USERNAME`, `MAIL_APP_PASSWORD`, and `app.mail.from` config. Missing config logs and skips; configured send failure throws `MailDeliveryException`.
- [ ] Run focused service test and confirm pass.

### Task 3: Integrate OTP Creation Into Manager Account Creation

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/auth/ManagerAccountService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/auth/ManagerAccountServiceTest.java`
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`

- [ ] Write failing test that `ManagerAccountService.createEmployeeAccount()` calls OTP service with the saved account.
- [ ] Run `./mvnw.cmd "-Dtest=ManagerAccountServiceTest" test` and confirm failure.
- [ ] Inject `AccountActivationOtpService` into `ManagerAccountService` and call it after `accountRepository.save(account)`.
- [ ] Update Spring context test mocks if needed.
- [ ] Run `./mvnw.cmd "-Dtest=ManagerAccountServiceTest,AccountActivationOtpServiceTest" test`.
- [ ] Run `./mvnw.cmd clean test`.
- [ ] Run a repo-wide search to confirm no default-password literal remains in source or docs.
- [ ] Commit with `feat: create activation otp for employee accounts` and push `main`.

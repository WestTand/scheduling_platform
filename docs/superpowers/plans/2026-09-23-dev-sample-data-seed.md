# Dev Sample Data Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed representative dev data after Docker volume resets so backend-mode UI can be tested immediately.

**Architecture:** Add a Spring Boot dev-profile `ApplicationRunner` that upserts employees/accounts and creates sample schedules/workflows through JPA repositories. Keep it idempotent with deterministic employee codes, usernames, schedule date/version, and existence checks.

**Tech Stack:** Spring Boot, JPA repositories, BCrypt `PasswordEncoder`, JUnit/Mockito.

---

### Task 1: Repository Support And Seeder Tests

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java`
- Create: `backend/src/test/java/scheduling/scheduling/auth/DevSampleDataSeederTest.java`

- [ ] Write failing unit tests for employee account seeding and schedule skip behavior.
- [ ] Run `./mvnw.cmd "-Dtest=DevSampleDataSeederTest" test` and verify failure because `DevSampleDataSeeder` does not exist.

### Task 2: Dev Sample Data Seeder

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/DevSampleDataSeeder.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/DevManagerAccountSeeder.java`
- Modify: `infra/docker-compose.dev.yml`
- Modify: `infra/.env.example`

- [ ] Implement `DevSampleDataSeeder` as `@Component`, `@Profile("dev")`, and `@Order(30)`.
- [ ] Add `@Order(20)` to `DevManagerAccountSeeder` so the manager exists before sample schedules.
- [ ] Wire `SEED_EMPLOYEE_PASSWORD` through Docker Compose and `.env.example`.
- [ ] Run `./mvnw.cmd "-Dtest=DevManagerAccountSeederTest,DevSampleDataSeederTest" test` and verify both test classes pass.

### Task 3: Runtime Verification

**Files:**
- Runtime only: Docker dev stack and local ignored `infra/.env`.

- [ ] Add `SEED_EMPLOYEE_PASSWORD=Employee@123` to local `infra/.env`.
- [ ] Rebuild/restart backend with `docker compose --env-file .env -f docker-compose.dev.yml up -d --build backend` from `infra`.
- [ ] Verify backend health at `http://localhost:8080/api/health`.
- [ ] Login as `emp001@example.com / Employee@123` and confirm role `EMPLOYEE`.
- [ ] Login as `manager@example.com / Manager@123` and confirm manager pages can list employees/workflows.

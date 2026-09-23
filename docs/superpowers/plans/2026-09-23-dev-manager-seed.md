# Dev Manager Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed a configured dev manager account automatically after Docker volume resets.

**Architecture:** Add a Spring Boot dev-profile startup component that reads environment-backed properties and upserts a manager account through existing repositories and `PasswordEncoder`. Keep production unchanged by activating the component only under the `dev` profile.

**Tech Stack:** Spring Boot, JPA repositories, BCrypt `PasswordEncoder`, JUnit/Mockito.

---

### Task 1: Dev Manager Seeder

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/auth/DevManagerAccountSeeder.java`
- Create: `backend/src/test/java/scheduling/scheduling/auth/DevManagerAccountSeederTest.java`
- Modify: `.env`

- [ ] Write failing unit tests for creating, updating, and skipping seed accounts.
- [ ] Run `./mvnw.cmd "-Dtest=DevManagerAccountSeederTest" test` and verify tests fail because the class does not exist.
- [ ] Implement `DevManagerAccountSeeder` as a `@Component`, `@Profile("dev")`, and `ApplicationRunner`.
- [ ] Add `SEED_MANAGER_USERNAME=manager@example.com` and `SEED_MANAGER_PASSWORD=Manager@123` to `.env`.
- [ ] Run `./mvnw.cmd "-Dtest=DevManagerAccountSeederTest" test` and verify tests pass.
- [ ] Restart backend container so the seeder runs.
- [ ] Verify login with `POST /api/auth/login` using the seeded manager credentials.

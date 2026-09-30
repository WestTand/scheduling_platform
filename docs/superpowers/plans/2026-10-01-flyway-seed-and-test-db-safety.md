# Flyway Seed And Test DB Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent backend tests from deleting local development data and move canonical/dev seed data from startup runners into SQL migrations.

**Architecture:** Backend tests must run against an isolated H2 test database by default. Flyway owns canonical seed data through SQL migrations; Hibernate may continue creating/updating non-migrated application tables while seed tables are bootstrapped by migration. Startup seeders are removed or disabled so app startup no longer rewrites seed data.

**Tech Stack:** Spring Boot 4, Maven, JPA/Hibernate, H2 test DB, PostgreSQL dev DB, Flyway SQL migrations.

---

### Task 1: Isolate Backend Tests From Dev DB

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/test/resources/application-test.properties`
- Modify: `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java`

- [ ] Add Surefire `spring.profiles.active=test` so every backend test defaults to test profile.
- [ ] Add H2 datasource and `ddl-auto=create-drop` in `application-test.properties`.
- [ ] Replace `OptimizationRunRecorderTransactionTest.cleanUp()` broad `deleteAll()` calls with scoped cleanup or rely on isolated H2 DB.
- [ ] Run `./mvnw.cmd -q test` and confirm no local Postgres data is touched.

### Task 2: Add Flyway And SQL Seed Migrations

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V1__bootstrap_seed_tables.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_canonical_shift_types.sql`
- Create: `backend/src/main/resources/db/migration/V3__seed_dev_accounts_and_employees.sql`
- Create: `backend/src/main/resources/db/migration/V4__seed_holidays.sql`
- Modify: `backend/src/main/resources/application.properties`

- [ ] Add Flyway dependencies.
- [ ] Enable Flyway with `baseline-on-migrate=true` for existing dev DBs.
- [ ] Add SQL migrations for canonical shift types, manager account, 20 employee accounts, and holidays.
- [ ] Keep migrations idempotent with `ON CONFLICT` upserts.

### Task 3: Remove Runtime Seed Mutation

**Files:**
- Delete or disable: `DevManagerAccountSeeder.java`, `DevSampleDataSeeder.java`, `StandardShiftTypeSeeder.java`, `HolidaySeedService.java`
- Update tests that directly target removed seeders.

- [ ] Stop startup `ApplicationRunner` seeders from mutating data.
- [ ] Remove/update seeder tests to assert Flyway migration resources exist instead of invoking Java seeders.
- [ ] Ensure app startup no longer rewrites seed rows.

### Task 4: Verify And Commit

**Files:**
- All changed files above plus frontend TS project fix from the same session.

- [ ] Run backend full suite: `./mvnw.cmd -q test`.
- [ ] Run frontend: `npm.cmd test`, `npm.cmd run build`, `npm.cmd run lint`.
- [ ] Commit and push branch.

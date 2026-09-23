# Dev Sample Data Seed Design

## Context

After Docker volume reset, the dev database only has canonical shift types and the seeded manager account. Testing backend mode requires employee accounts, schedules, requests, and assignments without manually creating records after every reset.

## Design

Add a dev-only backend seeder named `DevSampleDataSeeder`. It runs under Spring profile `dev`, after canonical shift types and the dev manager account are available.

The seeder is idempotent. Restarting the backend should update known sample rows where practical and avoid duplicates.

Seed data:

- Four active employees with active employee accounts:
  - `EMP001`, `Nguyễn Văn An`, `emp001@example.com`
  - `EMP002`, `Trần Thị Bình`, `emp002@example.com`
  - `EMP003`, `Lê Minh Châu`, `emp003@example.com`
  - `EMP004`, `Phạm Quốc Dũng`, `emp004@example.com`
- Employee password comes from `SEED_EMPLOYEE_PASSWORD`, defaulting to `Employee@123`.
- One current sample schedule period created by the dev manager.
- Work shifts for canonical shift codes `THONG_TAM`, `NGOAI_GIO_1`, and `TRUC_SANG`.
- Shift assignments for seeded employees.
- Leave requests covering pending and approved states.
- Preferences covering pending and approved states.
- Swap requests covering `PENDING_TARGET` and `PENDING_APPROVAL` states.

The seeder does not seed notifications or activity logs in this slice. Those can be created by real user actions during manual testing.

## Configuration

Add `SEED_EMPLOYEE_PASSWORD=Employee@123` to `infra/.env.example` and pass it through `infra/docker-compose.dev.yml`. The local ignored `infra/.env` should also receive the same value for the current machine.

## Testing

- Unit test verifies employee accounts are created with active `EMPLOYEE` role and encoded password.
- Unit test verifies missing canonical shift types cause schedule-related sample rows to be skipped without failing employee seeding.
- Runtime verification logs in as `emp001@example.com` and confirms employee APIs have seeded data.

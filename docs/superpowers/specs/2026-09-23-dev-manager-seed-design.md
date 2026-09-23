# Dev Manager Seed Design

## Context

After Docker volume reset, the dev database has no manager account. The app needs a repeatable way to create a dev manager without manual SQL or one-off scripts.

## Design

Add a dev-only startup seeder in the backend. It reads manager credentials from environment-backed properties:

- `SEED_MANAGER_USERNAME`
- `SEED_MANAGER_PASSWORD`

When both values are present and the active Spring profile includes `dev`, the seeder upserts one account:

- username from `SEED_MANAGER_USERNAME`
- password encoded with the app `PasswordEncoder`
- role `MANAGER`
- `isActive=true`
- status `ACTIVE`
- no employee link

If the username already exists, the seeder updates role/password/status so the configured dev login always works after environment changes.

If either variable is missing or blank, the seeder does nothing.

## Testing

- Unit test creates a manager when both seed variables are present.
- Unit test updates an existing account to manager/active with a refreshed password.
- Unit test does nothing when credentials are blank.

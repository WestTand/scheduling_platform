# Admin Account APIs Design

## Context

Phase 3 Admin Core already has employee list/search/detail/update/lock/unlock APIs and manager account creation/resend/reset APIs. The next missing Phase 3 slice is general account administration: list accounts and lock/unlock employee accounts directly.

This work builds on the current Spring Boot backend and keeps the existing auth model:

- `Account.isActive` controls whether the account can authenticate or use existing access tokens.
- `Account.status` tracks lifecycle state: `ACTIVE`, `PENDING_ACTIVATION`, `FORCE_PASSWORD_CHANGE`, `INACTIVE`.
- `RefreshTokenService.revokeAllForAccount` invalidates refresh tokens after lock/reset operations.

## Scope

Implement backend APIs under `/api/admin/accounts`:

- `GET /api/admin/accounts?query=&role=&status=`
- `POST /api/admin/accounts/{accountId}/lock`
- `POST /api/admin/accounts/{accountId}/unlock`

All endpoints are available to `MANAGER` and `ADMIN`. `EMPLOYEE` must receive `403`.

This slice does not include creating manager/admin accounts, deleting accounts, changing account roles, or frontend screens.

## API Behavior

### List Accounts

`GET /api/admin/accounts` returns accounts sorted by repository default order for now. Filters are optional:

- `query`: trim whitespace. Match username, linked employee code, or linked employee full name case-insensitively.
- `role`: if present, parse as `AccountRole`.
- `status`: if present, parse as `AccountStatus`.

Response fields:

- account id
- username
- role
- status
- isActive
- linked employee summary when present: employee id, employee code, full name, email, status

Invalid enum filter values should fail with the existing generic Spring MVC bad-request handling.

### Lock Account

`POST /api/admin/accounts/{accountId}/lock`:

- Find account or throw `AccountNotFoundException`.
- Set `isActive=false`.
- If `status == ACTIVE`, set `status=INACTIVE`.
- If `status` is `PENDING_ACTIVATION` or `FORCE_PASSWORD_CHANGE`, keep that lifecycle status so unlock cannot bypass activation/password-change.
- Revoke all refresh tokens for the account.
- Return `204 No Content`.

### Unlock Account

`POST /api/admin/accounts/{accountId}/unlock`:

- Find account or throw `AccountNotFoundException`.
- Set `isActive=true`.
- If `status == INACTIVE`, set `status=ACTIVE`.
- If `status` is `PENDING_ACTIVATION` or `FORCE_PASSWORD_CHANGE`, keep that lifecycle status.
- Return `204 No Content`.

## Components

Add an admin account component parallel to existing admin employee APIs:

- `AdminAccountController`: HTTP endpoints and role protection.
- `AdminAccountService`: account listing, lock, unlock.
- DTOs under `scheduling.scheduling.admin.dto` for account and employee summary responses.
- Repository search support in `AccountRepository` using a JPQL query with optional filters.

Use existing exceptions where possible:

- `AccountNotFoundException` for missing account ids.
- Existing `AuthExceptionHandler` already maps account-not-found style auth exceptions.

## Data Flow

List flow:

1. Controller accepts optional query/role/status strings.
2. Service normalizes blank query to `null` and parses role/status when supplied.
3. Repository returns matching accounts, including linked employee data through entity relation access.
4. Service maps entities to DTOs.

Lock/unlock flow:

1. Controller receives account id.
2. Service loads account.
3. Service updates `isActive` and lifecycle-safe status.
4. Lock revokes refresh tokens.
5. Transaction commits.

## Testing

Add service and controller tests covering:

- List without filters returns account DTOs with linked employee summaries.
- Query/role/status filters are passed correctly and produce expected DTOs.
- Locking an active account sets `isActive=false`, `status=INACTIVE`, and revokes refresh tokens.
- Locking a pending activation account sets `isActive=false` but keeps `PENDING_ACTIVATION`.
- Unlocking an inactive account sets `isActive=true`, `status=ACTIVE`.
- Unlocking a force-password-change account sets `isActive=true` but keeps `FORCE_PASSWORD_CHANGE`.
- Missing account throws `AccountNotFoundException`.
- Controller allows `MANAGER`/`ADMIN` and rejects `EMPLOYEE`.

Run full backend verification with `./mvnw clean test` or `mvnw.cmd clean test` on Windows.

## Notes

This design deliberately mirrors the lifecycle-safe lock/unlock behavior from the recent auth logic review so admin account management cannot bypass OTP activation or required password change.

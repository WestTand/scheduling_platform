# Auth JWT Refresh Cookie Design

## Context

The backend currently contains entity mappings, a health endpoint, and a temporary security filter chain in `HealthController`. There are no auth controllers, repositories, services, or JWT utilities yet. The project uses Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, and the existing `accounts` and `employees` tables from `docs/database-design-v2.md`.

## Goal

Implement employee self-registration, login, refresh, logout, and current-user lookup using JWT access tokens and refresh tokens stored in an HTTP-only cookie. Public registration creates an `EMPLOYEE` account and its linked `Employee` profile.

## Non-Goals

- Public manager registration.
- Role/permission management beyond the existing `MANAGER` and `EMPLOYEE` enum.
- Frontend implementation.
- OAuth/social login.
- Email verification or password reset.

## Approach

Use a short-lived JWT access token plus an opaque refresh token stored server-side. The refresh token value is a random secret sent only in an HTTP-only cookie. The database stores a hash of the refresh token and metadata so the backend can revoke, rotate, and expire refresh sessions.

This approach is preferred over self-contained refresh JWTs because logout and token theft response require server-side invalidation.

## Data Model

Add a `refresh_tokens` table and matching JPA entity.

Columns:

| Column | Type | Constraint | Notes |
|---|---|---|---|
| refresh_token_id | BIGSERIAL | PK | Entity overrides `BaseEntity.id` to this column. |
| account_id | BIGINT | FK -> accounts, NOT NULL | Token owner. |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL | Hash of the opaque refresh token. |
| expires_at | TIMESTAMP | NOT NULL | Absolute expiry time. |
| revoked_at | TIMESTAMP | NULL | Set when logout or rotation revokes token. |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp. |

Only non-revoked and non-expired refresh tokens are valid.

## API Design

### `POST /api/auth/register`

Request:

```json
{
  "username": "employee01",
  "password": "secret123",
  "employeeCode": "EMP001",
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "phone": "0900000000"
}
```

Behavior:

- Validate required fields: `username`, `password`, `employeeCode`, `fullName`.
- Reject duplicate `username` and duplicate `employeeCode`.
- Create `Employee` with status `ACTIVE`.
- Create linked `Account` with role `EMPLOYEE`, BCrypt password hash, and `isActive=true`.
- Return basic account/employee information. Registration does not need to auto-login.

### `POST /api/auth/login`

Request:

```json
{
  "username": "employee01",
  "password": "secret123"
}
```

Behavior:

- Authenticate active account by username/password.
- Update `last_login`.
- Return access token in JSON.
- Create refresh token record and set the raw token in an HTTP-only cookie.

Response:

```json
{
  "accessToken": "jwt-value",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "account": {
    "id": 1,
    "username": "employee01",
    "role": "EMPLOYEE"
  }
}
```

### `POST /api/auth/refresh`

Behavior:

- Read refresh token from cookie.
- Hash and find matching non-revoked token.
- Reject missing, revoked, expired, or unknown token.
- Rotate refresh token: revoke old token, create new one, update cookie.
- Return a fresh access token.

### `POST /api/auth/logout`

Behavior:

- Read refresh token cookie if present.
- Revoke matching token if it exists.
- Clear refresh token cookie.
- Return success even when no cookie exists.

### `GET /api/auth/me`

Behavior:

- Requires valid access JWT.
- Return current authenticated account details and linked employee details when present.

## Security Design

- Access tokens are JWTs signed with `app.jwt.secret`.
- Access token TTL defaults to 15 minutes.
- Refresh token TTL defaults to 7 days.
- Passwords are stored with BCrypt.
- Refresh tokens are opaque random values, not JWTs.
- Refresh token hashes are stored in DB. Raw refresh tokens are never persisted.
- Refresh cookie settings for local development:
  - `HttpOnly=true`
  - `Secure=false`
  - `SameSite=Lax`
  - `Path=/api/auth`
- Production can set `Secure=true` through configuration when HTTPS is used.
- Public endpoints: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/health`, and Swagger/OpenAPI endpoints.
- Other endpoints require `Authorization: Bearer <accessToken>`.

## Backend Components

- `RefreshToken` entity under `entity`.
- `AccountRepository`, `EmployeeRepository`, `RefreshTokenRepository`.
- `AuthController` for auth endpoints.
- `AuthService` for registration, login, refresh, logout, and me lookup.
- `JwtService` for creating and validating JWT access tokens.
- `RefreshTokenService` for opaque token generation, hashing, validation, rotation, and revocation.
- `SecurityConfig` to replace the temporary health-only security config.
- `JwtAuthenticationFilter` to authenticate bearer tokens.
- DTOs for request and response payloads.
- `AuthProperties`/`JwtProperties` for configurable TTLs, cookie name, secret, and secure cookie flag.

## Error Handling

Return stable JSON errors with appropriate HTTP status codes:

- `400 Bad Request`: validation errors.
- `401 Unauthorized`: invalid login credentials, invalid/expired access token, invalid refresh token.
- `409 Conflict`: duplicate username or duplicate employee code.

Do not reveal whether username or password specifically failed during login.

## Testing Strategy

- Service tests for registration success and duplicate username/employee code.
- Service tests for login with valid credentials, invalid credentials, and inactive account.
- Service tests for refresh token rotation, revoked token rejection, and expired token rejection.
- Controller/security tests for public auth endpoints and protected `/api/auth/me`.
- JWT utility tests for claim generation and invalid token rejection.

## Acceptance Criteria

- Employee registration creates exactly one `Employee` and one linked `Account` with role `EMPLOYEE`.
- Passwords are BCrypt hashes, not raw passwords.
- Login returns a valid access JWT and sets an HTTP-only refresh cookie.
- Refresh endpoint rotates refresh tokens and revokes the previous token.
- Logout revokes the current refresh token and clears the cookie.
- `/api/auth/me` works with a valid bearer token and rejects missing/invalid tokens.
- Existing `/api/health` remains public.
- Backend tests pass with the new auth tests.

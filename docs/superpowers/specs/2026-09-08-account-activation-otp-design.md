# Account Activation OTP Design

## Context

The current auth implementation on `main` supports public employee registration through `POST /api/auth/register`. That no longer matches the required business flow. Employee accounts must be created by managers, and employees must activate their accounts through an email OTP before normal login.

## Goal

Replace public self-registration with a manager-created account flow where the employee receives an email OTP, verifies the OTP during first login/activation, changes the temporary password, and then uses normal JWT login/refresh/logout.

## Account Lifecycle

- `PENDING_ACTIVATION`: manager created the account; employee has not verified email OTP.
- `FORCE_PASSWORD_CHANGE`: email OTP is verified or manager reset the password; employee must set a new password before normal login.
- `ACTIVE`: employee can login, refresh token, and call protected APIs.
- `INACTIVE`: account is locked and cannot login, refresh, or call protected APIs.

`Account.isActive` remains for hard lock/unlock. A new account status field stores lifecycle state.

## Manager Flow

- Manager creates an employee profile and account with email.
- Backend generates a temporary password and an OTP.
- Backend stores only hashed OTP and hashed temporary password.
- Backend sends email containing activation instructions and OTP.
- Manager can resend activation OTP.
- Manager can reset password for forgotten-password cases.
- Reset password revokes existing refresh tokens, sets account to `FORCE_PASSWORD_CHANGE` or `PENDING_ACTIVATION`, and sends a new email/OTP.

## Employee Flow

- Employee enters username/password.
- If account is `PENDING_ACTIVATION`, backend does not issue JWT; response says OTP verification is required.
- Employee submits OTP.
- If OTP is valid, backend marks email verified and moves account to `FORCE_PASSWORD_CHANGE`.
- Employee changes password.
- Backend marks account `ACTIVE` and allows normal login.
- If employee forgets password, there is no public forgot-password endpoint. Employee must contact manager.

## API Changes

- Remove public `POST /api/auth/register`.
- Add manager endpoint `POST /api/manager/accounts` to create employee account.
- Add manager endpoint `POST /api/manager/accounts/{accountId}/activation/resend`.
- Add manager endpoint `POST /api/manager/accounts/{accountId}/password/reset`.
- Add public endpoint `POST /api/auth/activation/verify-otp`.
- Add public endpoint `POST /api/auth/password/change-required`.
- Keep `POST /api/auth/login`, `/refresh`, `/logout`, and `GET /api/auth/me`.

## Login State Response

`POST /api/auth/login` keeps the existing success response for `ACTIVE` accounts: access token in the JSON body and refresh token in an HttpOnly cookie.

If credentials are valid but the account is `PENDING_ACTIVATION`, login returns `200 OK` without issuing access or refresh tokens. The response body contains `status = OTP_REQUIRED`, `accountId`, and `username` so the frontend can route the user to OTP verification.

If credentials are valid but the account is `FORCE_PASSWORD_CHANGE`, login returns `200 OK` without issuing access or refresh tokens. The response body contains `status = PASSWORD_CHANGE_REQUIRED`, `accountId`, and `username` so the frontend can route the user to required password change.

If credentials are invalid, `isActive` is false, or the account is `INACTIVE`, login keeps the generic authentication failure response and does not reveal which check failed.

## Security Rules

- Only managers can create employee accounts, resend OTP, reset password, lock, or unlock accounts.
- `PENDING_ACTIVATION` and `FORCE_PASSWORD_CHANGE` accounts cannot receive access/refresh tokens for normal API use.
- Refresh tokens are revoked when manager resets password or locks an account.
- OTP values are random numeric codes, short-lived, one-time use, and stored hashed.
- Login responses must not reveal whether username or password specifically failed.

## Email

Initial implementation may use a mail service abstraction with a development logger implementation if SMTP is not configured. The interface should be stable so SMTP can be plugged in later.

## Acceptance Criteria

- Public self-registration is removed from security config and controller.
- Manager can create an employee account with email.
- New account cannot login normally until OTP verification and forced password change are completed.
- Valid OTP moves account to forced password change.
- Forced password change activates the account.
- Forgot password is manager-driven reset only.
- Reset/lock revokes refresh tokens.
- Tests cover manager creation, OTP verification, forced password change, inactive/pending login rejection, and reset behavior.

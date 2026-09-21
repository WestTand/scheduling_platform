# Self-Service Resend Activation OTP Design

## Context

Activation OTP resend is currently available only to managers through `POST /api/manager/accounts/{accountId}/activation/resend`. That works operationally, but it forces an employee to contact a manager when their activation OTP expires.

The activation page already has a resend action. In backend mode it currently tells the user to contact a manager instead of sending a new OTP.

## Goal

Allow a pending employee account to request a new activation OTP from the activation page without manager involvement.

## Non-Goals

- Do not enable resend by email alone.
- Do not change the manager resend endpoint.
- Do not add rate limiting in this slice.
- Do not switch login to email-based lookup in this slice.

## Backend Design

Add a public auth endpoint:

```http
POST /api/auth/activation/resend
```

Request body:

```json
{
  "username": "phat143",
  "temporaryPassword": "1111@"
}
```

Successful response:

```http
204 No Content
```

The endpoint delegates to `AuthService.resendActivationOtp(request)`.

Validation and authorization rules:

- Find the account by `username`.
- Require `isActive = true`.
- Require `status = PENDING_ACTIVATION`.
- Require `temporaryPassword` to match the stored password hash.
- If validation passes, call the existing activation OTP service to create and send a new OTP.

Failure behavior:

- Unknown username or wrong temporary password returns the existing authentication failure response.
- Existing but non-pending accounts return the existing invalid account state response.
- Invalid request shape returns the existing validation failure response.

Security config must treat this endpoint as public, matching login and activation verification.

## Frontend Design

Add backend client method:

```ts
backendAuthService.resendActivationOtp(username, temporaryPassword)
```

Update `authService.resendActivationOtp` so backend mode calls the new public endpoint. Mock mode keeps using the existing mock account resend behavior.

Update `AccountActivationPage.handleResendOtp`:

- Backend mode no longer shows "contact manager".
- It requires the username and temporary password already held by the activation flow.
- It calls the self-service resend endpoint.
- It clears the OTP field, restarts the countdown, and shows a success message.

If username or temporary password is missing, the UI shows a clear message asking the user to return to login and enter the temporary credentials again.

## Testing

Backend tests should cover:

- Pending active account with correct temporary password returns `204` and sends/regenerates OTP.
- Wrong temporary password returns unauthorized.
- Active account not in `PENDING_ACTIVATION` returns invalid account state.
- Endpoint is accessible without JWT.

Frontend verification should cover:

- Production build passes.
- Activation page resend in backend mode calls `POST /api/auth/activation/resend`.
- Missing temporary password displays a user-facing error instead of calling the API.

## Risks

The endpoint can be abused to send mail if an attacker knows both username and temporary password. This is acceptable for this slice because the temporary password is already the activation credential. If abuse becomes a concern, add per-account or per-username rate limiting later.

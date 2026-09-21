# Phase 9B Auth Lifecycle Design

## Goal

Complete the frontend auth lifecycle using the backend endpoints that already exist, without adding new backend APIs in this slice.

The supported end-to-end path is:

1. User submits username/email and password on login.
2. Backend returns either a normal auth response or a pending login state.
3. `OTP_REQUIRED` sends the user to account activation.
4. User verifies OTP through `/api/auth/activation/verify-otp`.
5. User sets a new password through `/api/auth/password/change-required`.
6. Frontend stores the returned token/user and redirects to the correct dashboard.

## Scope

In scope:

- Route backend pending login states from `LoginPage` to the auth lifecycle page.
- Wire account activation OTP verification to the backend in backend mode.
- Wire required password change to the backend in backend mode.
- Keep mock mode behavior working for existing demo accounts and activation page flows.
- Store auth state after password change succeeds, then redirect by role.
- Show clear user-facing copy when resend OTP is unavailable in backend mode.

Out of scope:

- Adding a public backend resend OTP endpoint.
- Reworking manager account resend/reset APIs.
- Migrating employee, shift, schedule, leave, swap, or notification pages to backend APIs.
- Redesigning auth UI beyond the state/copy needed for the lifecycle.

## Existing Backend Contract

The backend already exposes these public auth endpoints:

- `POST /api/auth/login`
  - Success: `AuthResponse` and refresh cookie.
  - Pending activation: `LoginStateResponse { status: "OTP_REQUIRED", accountId, username }`.
  - Forced password change: `LoginStateResponse { status: "PASSWORD_CHANGE_REQUIRED", accountId, username }`.
- `POST /api/auth/activation/verify-otp`
  - Body: `VerifyActivationOtpRequest { accountId, otp }`.
  - Response: `ActivationStateResponse { status: "PASSWORD_CHANGE_REQUIRED", accountId, username }`.
- `POST /api/auth/password/change-required`
  - Body: `RequiredPasswordChangeRequest { accountId, currentPassword, newPassword }`.
  - Response: `AuthResponse` and refresh cookie.

The backend does not expose a public self-service resend OTP endpoint. The existing resend endpoint is manager-protected, so backend-mode resend will not be attempted in this slice.

## Frontend Architecture

### Types

Extend `frontend/src/types/backendAuth.ts` with:

- `BackendActivationStateResponse`, matching `ActivationStateResponse`.
- Request/response helper types only if they make service code clearer.

The current `BackendLoginStateResponse` can be reused for activation state if the structure stays identical, but the name should stay clear at call sites.

### Backend Auth Service

Extend `frontend/src/services/backendAuthService.ts` with:

- `verifyActivationOtp(accountId: number, otp: string)`.
- `changeRequiredPassword(accountId: number, currentPassword: string, newPassword: string)`.

`changeRequiredPassword` returns the same shape as login success: `{ accessToken, user }`.

### Auth Service Boundary

Extend `frontend/src/services/authService.ts` so UI code continues to call one auth boundary:

- `verifyActivationOtp(accountId, otp)` delegates to backend in backend mode and keeps mock behavior in mock mode.
- `completeRequiredPasswordChange(accountId, currentPassword, newPassword, rememberMe)` delegates to backend in backend mode, stores auth with `setStoredAuth`, and returns `User`.

Mock mode keeps existing `accountService` behavior so the demo activation flow continues to work.

### Login Page

`LoginPage` catches `BackendLoginStateError` thrown by `authService.login`.

- For `OTP_REQUIRED`, navigate to `/activate-account` with `location.state` containing `accountId`, `username`, `currentPassword`, and `status`.
- For `PASSWORD_CHANGE_REQUIRED`, navigate to `/activate-account` with `location.state` containing the same values and an initial step of password change.
- If navigation state is unavailable later, the activation page still allows manual username/password entry.

Use `location.state` instead of query parameters for `accountId` and current password so sensitive values do not appear in URLs.

### Account Activation Page

Keep the existing three-step UI structure but make it mode-aware:

1. Identify account and temporary password.
   - Mock mode keeps `accountService.requestActivation`.
   - Backend mode skips public OTP request because backend sends OTP during manager account creation/resend. If the page is reached from login state, it should go directly to OTP or password-change step.
2. Verify OTP.
   - Mock mode uses `accountService.verifyActivationOtp`.
   - Backend mode uses `authService.verifyActivationOtp`.
   - Success moves to password-change step with backend `accountId` and username preserved.
3. Change required password.
   - Mock mode uses `accountService.setNewPassword` and then returns to login, preserving current demo behavior unless already authenticated flow is easy to support.
   - Backend mode uses `authService.completeRequiredPasswordChange`, stores token/user, and redirects to manager or employee dashboard.

Backend-mode resend OTP should show a non-blocking message: “Vui lòng liên hệ quản lý để gửi lại mã OTP.” It must not call the manager-protected resend endpoint.

## Data Flow

### Pending Activation

1. User logs in with username and temporary password.
2. Backend returns `OTP_REQUIRED` with `accountId` and `username`.
3. Login page navigates to activation page with state.
4. Activation page asks for OTP.
5. OTP verification returns `PASSWORD_CHANGE_REQUIRED`.
6. Page asks for a new password.
7. Password change returns auth tokens.
8. Frontend stores auth and redirects by role.

### Forced Password Change

1. User logs in with username and current password.
2. Backend returns `PASSWORD_CHANGE_REQUIRED` with `accountId` and `username`.
3. Login page navigates directly to password-change step.
4. Password change returns auth tokens.
5. Frontend stores auth and redirects by role.

## Error Handling

- Validation errors remain local for missing fields, 6-digit OTP length, password requirements, and password confirmation mismatch.
- Backend `ApiError` messages are shown directly when available.
- Invalid OTP shows a specific OTP error if backend returns one; otherwise use the existing Vietnamese fallback.
- Password-change failure keeps the user on the password step and does not store auth.
- Login-state navigation clears any stale stored auth before entering activation/password change.

## Testing And Verification

Manual/build verification for this slice:

- `npm.cmd run build` in `frontend/`.
- Backend-mode build with `VITE_USE_MOCK_API=false` and `VITE_API_BASE_URL=http://localhost:8080`.
- Mock login and mock activation page still compile and keep existing behavior.
- Backend-mode login pending states route to the correct activation/password steps.

Add frontend tests only if the repo already has a practical test runner configured during implementation. If not, rely on TypeScript build and targeted code review for this slice.

## Documentation Updates

Update `docs/project_plan.md` after implementation to mark only the completed auth lifecycle items:

- Login lifecycle routing to OTP and forced password change.
- Account activation OTP UI wired to backend verify endpoint.
- Required password change UI wired to backend endpoint.

Do not mark public resend OTP or broader page API migrations complete.

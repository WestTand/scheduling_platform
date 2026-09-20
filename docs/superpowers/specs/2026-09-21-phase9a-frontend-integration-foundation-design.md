# Phase 9A Frontend Integration Foundation Design

## Purpose

Phase 9A establishes the frontend foundation needed to connect the existing mock-first React UI to the real backend safely. The goal is not to refactor every page at once. The goal is to add a reliable API mode switch, shared HTTP client, real auth flow, backend DTO types, and adapter pattern so later Phase 9 slices can migrate pages incrementally without reshaping backend contracts to match old mocks.

## Scope

In scope:

- Add frontend API configuration based on Vite environment variables.
- Support mock mode and backend mode through one service-facing contract.
- Add a shared `fetch`-based API client.
- Parse backend errors into user-displayable messages.
- Attach access tokens to protected requests.
- Send cookies on auth requests so backend refresh-token cookies work.
- Add backend auth DTO types.
- Add auth adapter functions that map backend responses to existing frontend `User` objects.
- Connect auth service and auth context to real backend login/me/refresh/logout when backend mode is enabled.
- Keep the current mock auth flow working.
- Verify with `npm run build`.

Out of scope:

- Migrating every manager/employee page to backend APIs.
- Redesigning the UI or layout.
- Adding export, notification, or activity log UI integration.
- Removing old mock data.
- Adding a frontend test runner.
- Changing backend APIs to match frontend mock models.

## Runtime Configuration

Add `frontend/src/config/api.ts` with these runtime values:

- `apiBaseUrl`: from `import.meta.env.VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.
- `useMockApi`: true unless `import.meta.env.VITE_USE_MOCK_API === 'false'`.

This keeps the default development behavior safe for the current frontend while allowing backend integration with:

```powershell
$env:VITE_USE_MOCK_API="false"
$env:VITE_API_BASE_URL="http://localhost:8080"
npm run dev
```

## API Client

Add `frontend/src/services/apiClient.ts`.

Responsibilities:

- Build URLs from `apiBaseUrl` and relative API paths.
- Set `Content-Type: application/json` for JSON requests.
- Attach `Authorization: Bearer <accessToken>` when a token exists.
- Set `credentials: 'include'` so backend refresh-token cookies are sent.
- Parse JSON responses when present.
- Return empty success for no-content responses.
- Convert backend error bodies into an `ApiError` with `status`, optional `code`, and display-safe `message`.

The client should not know about React. It should expose plain async functions used by services.

## Auth Integration

Existing frontend auth currently uses `mockLogin`, stores a mock token, and restores a serialized `User`. Phase 9A keeps that mock path intact and adds backend mode.

Backend mode endpoints:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`

The login form currently has an `email` field. In backend mode, this value is sent as `username` because the backend auth contract is username/password.

Expected backend-mode behavior:

1. Login calls `/api/auth/login` with `{ username, password }`.
2. Access token is stored using the existing auth storage key.
3. Backend user/account response is adapted into the existing frontend `User` shape.
4. Refresh token remains in the backend-managed HttpOnly cookie.
5. Logout calls backend `/api/auth/logout` and clears frontend auth state even if the network request fails.
6. On reload in backend mode, the app tries `/api/auth/me` if an access token is present.
7. If `/api/auth/me` fails due to auth expiry, the app tries `/api/auth/refresh`, stores the new access token, then retries `/api/auth/me` or adapts the refresh response if it includes user data.
8. If refresh fails, frontend auth is cleared and protected routes redirect to login.

## DTOs And Adapters

Add backend DTO types under `frontend/src/types/backendAuth.ts`.

These types should describe only raw backend responses used by Phase 9A, such as:

- login response with access token and account/user fields.
- `/api/auth/me` response.
- refresh response.
- account role/status values returned by backend.

Add `frontend/src/adapters/authAdapter.ts`.

Adapter responsibilities:

- Convert backend role strings to frontend `Role` enum values.
- Convert backend account/profile identity into existing frontend `User` shape.
- Preserve enough fields for routing and layout: `id`, `name`, `email`, `role`, and optional department/avatar values.
- Use safe fallbacks for missing optional profile fields.

Pages and components should not parse raw backend auth DTOs directly.

## Service Boundary

Update `frontend/src/services/authService.ts` so it exposes service functions that choose mock or backend mode internally:

- `login(credentials, rememberMe)`
- `logout()`
- `restoreStoredUser()`
- token storage helpers already present can stay, but backend mode must store the real access token instead of a mock token.
- Add `bootstrapAuth()` to validate/refresh backend sessions on reload while keeping mock mode restoration unchanged.

Mock mode must continue to use current mock data and behavior.

Backend mode must use `backendAuthService` and the adapter layer.

## Auth Context

Update `frontend/src/contexts/AuthContext.tsx` conservatively:

- Keep the existing `AuthContextValue` public contract; implement bootstrapping internally in the provider/service layer.
- Preserve existing login/logout usage by `LoginPage`, routes, and layout.
- Set loading state while backend bootstrapping/login/logout is in progress.
- Clear auth on unrecoverable backend auth failures.
- Do not redirect manually inside the context; continue relying on route guards and existing navigation behavior.

If adding bootstrapping makes the context more complex, keep the logic in a small service helper so React code stays readable.

## Error Handling

Backend error parsing should support:

- `{ code, message }`
- `{ message }`
- plain text response body
- empty response body

Fallback message:

```text
Không thể kết nối máy chủ. Vui lòng thử lại.
```

Login failures should surface the backend message when available. Logout should always clear local auth state after attempting backend logout in backend mode.

## Verification

Required verification for Phase 9A implementation:

- `npm run build` from `frontend/`.
- Mock mode still builds with default environment values.
- Backend mode builds with `VITE_USE_MOCK_API=false`.
- Backend and AI test suites are not required unless backend or AI files are changed.

Manual smoke checks after implementation:

- Mock login still works with existing seeded users.
- Backend-mode login sends username/password to backend.
- Protected routes still redirect unauthenticated users.
- Logout clears frontend state.

## Later Phase 9 Slices

After Phase 9A, page migration should happen in focused slices:

- Employee management backend integration.
- Shift type backend integration.
- Schedule periods, work shifts, and assignments integration.
- AI schedule generation UI alignment.
- Leave/preference/swap workflows.
- Notification inbox, activity logs, and export download buttons.

Each slice should reuse the Phase 9A API client and adapter pattern instead of mapping raw backend DTOs inside page components.

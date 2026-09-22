# Phase 9I Work Preferences Backend Integration Design

## Context

Work Preferences are currently split across backend and frontend maturity levels. The backend already exposes employee preference APIs for list, create, update, and cancel. The manager Work Preferences page still uses mock data through `preferenceService`, and the employee preferences page maintains local seed state instead of using the backend service.

Phase 9I connects Work Preferences end-to-end for both employee and manager flows while preserving mock mode behavior.

## Goal

When `VITE_USE_MOCK_API=false`, both employee and manager Work Preferences screens should use backend APIs for listing and state-changing operations. Mock mode should continue to behave as it does today.

## Backend Design

Reuse the existing `EmployeePreferenceResponse` DTO as the shared preference response shape. Add manager-facing preference APIs under `/api/admin/preferences`.

Manager API capabilities:
- `GET /api/admin/preferences` lists all preferences with optional filters: `status`, `from`, and `to`.
- `POST /api/admin/preferences/{preferenceId}/approve` approves a pending preference.
- `POST /api/admin/preferences/{preferenceId}/reject` rejects a pending preference.

Repository additions:
- Add a manager search query that fetches employee and shift type data and applies optional status/date filters.
- Add a pessimistic-lock lookup for manager review by preference id.

Validation rules:
- Employee create/update/cancel keeps existing rules: only own pending preferences are mutable by employees.
- Manager review only accepts `PENDING` preferences.
- Manager approve/reject transitions set status to `APPROVED` or `REJECTED`.
- If the existing `Preference` entity does not persist manager notes, Phase 9I will not add a new persistence column; frontend will not require notes for approval, and rejection notes will remain mock-only until a dedicated audit/note design is introduced.

## Frontend Design

Add backend preference DTO types and an adapter from backend preferences to the current `WorkPreference` UI model.

Mapping decisions:
- Backend `WANT` maps to current `PREFERRED`.
- Backend `AVOID` maps to current `AVOID`.
- Backend `preferenceDate` maps to a month string using `YYYY-MM` for the existing manager filter model.
- Backend shift type data maps to `relatedShiftCode` when present.
- Backend status maps directly for `PENDING`, `APPROVED`, `REJECTED`, and `CANCELLED` where supported by the UI model.

Update `preferenceService` to switch by `apiConfig.useMockApi`:
- Mock mode keeps existing in-memory seed behavior.
- Backend mode delegates to `backendPreferenceService`.

Manager page behavior:
- `/manager/preferences` lists backend preferences through `preferenceService.list`.
- Approve/reject actions call backend manager review endpoints.
- Rejection note validation should only apply in mock mode if backend does not persist notes.

Employee page behavior:
- `/employee/preferences` lists backend preferences.
- Create/update/cancel call existing employee preference backend endpoints.
- The page should use backend-backed state instead of local seed state in backend mode.

## Non-Goals

- Do not add dashboard/sidebar live preference counts.
- Do not introduce manager note persistence or activity-log review notes for preferences.
- Do not change AI scheduling preference consumption logic.
- Do not redesign the Work Preferences UI.

## Testing

Backend:
- Add `AdminPreferenceServiceTest` for manager list/review rules.
- Add `AdminPreferenceControllerTest` for authorization, list, approve, and reject endpoints.
- Preserve existing employee preference tests.

Frontend:
- Add adapter tests for backend-to-UI preference mapping.
- Add backend preference service tests for manager and employee endpoints.
- Run full frontend tests, lint, and build.

Verification:
- Run focused backend preference tests.
- Run `./mvnw.cmd -DskipTests package`.
- Run `npm.cmd test`, `npm.cmd run lint`, and `npm.cmd run build`.

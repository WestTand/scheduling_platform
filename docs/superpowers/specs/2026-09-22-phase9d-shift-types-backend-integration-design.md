# Phase 9D Shift Types Backend Integration Design

## Context

Phase 9C connected the manager employee/account management page to the real backend. The next frontend integration slice should follow the same mock/backend adapter pattern for the existing manager shift type page.

The frontend already has `ShiftTypesPage` and `shiftTypeService` backed by mock data. The backend already exposes admin shift type APIs through `AdminShiftTypeController` and `AdminShiftTypeService`.

## Goal

Connect the existing `Quản lý loại ca trực` manager page to backend shift type APIs when `VITE_USE_MOCK_API=false`, while preserving current mock mode behavior.

## Scope

Included:

- Add backend DTO types for shift type responses and requests.
- Add an adapter from backend shift type DTOs to existing frontend `ShiftType` models.
- Add `backendShiftTypeService` for list, create, update, status toggle, and delete operations.
- Update `shiftTypeService` to route to backend or mock by `apiConfig.useMockApi`.
- Keep the current `ShiftTypesPage` UI structure and behavior.
- Verify default mock build and backend-mode build.
- Browser smoke test the manager shift type flow against the local backend.

Excluded:

- No new Work Shift management UI.
- No schedule generation/edit/detail integration changes.
- No redesign of shift type forms beyond small contract-alignment fixes if required.
- No backend API redesign unless the existing endpoint contract cannot support the current page behavior.

## Architecture

Use the same boundary established by Phase 9C:

- Page components consume frontend UI models only.
- Backend response shapes stay inside service/adapter files.
- `shiftTypeService` remains the public service used by `ShiftTypesPage`.
- Mock mode continues to use existing seed data and validation.

Planned frontend units:

- `frontend/src/types/backendShiftType.ts`: backend request/response shapes.
- `frontend/src/adapters/shiftTypeAdapter.ts`: backend DTO to frontend `ShiftType` and form/request mapping.
- `frontend/src/services/backendShiftTypeService.ts`: authenticated API calls.
- `frontend/src/services/shiftTypeService.ts`: mode switch between mock and backend service.

## Data Flow

List flow:

1. `ShiftTypesPage` calls `shiftTypeService.list({ page, pageSize, search, status })`.
2. `shiftTypeService` calls mock logic or `backendShiftTypeService.list`.
3. Backend DTOs are adapted to existing `PaginatedResult<ShiftType>`.
4. The page renders the unchanged table.

Mutation flow:

1. The page submits existing `ShiftTypeFormValues`.
2. `shiftTypeService.create/update` maps the form to backend request fields.
3. Backend response is adapted back into `ShiftType`.
4. The page refreshes list data using its current reload behavior.

Status/delete flow:

- Status toggle should call the backend status operation if available, or update endpoint if the backend models status updates through normal update.
- Delete should call backend delete. If backend rejects deleting a shift type that is already used, the service surfaces the backend message and the page displays it through the existing alert/error path.

## Error Handling

- Preserve the page's current loading and error states.
- API errors from `apiClient` should be surfaced as user-readable messages where possible.
- Contract mismatches should fail fast in adapter/service code rather than silently producing broken UI values.

## Testing

- Run backend focused shift type tests if backend code changes are required.
- Run `npm.cmd run build` in `frontend` with default mock mode.
- Run backend-mode build with `VITE_USE_MOCK_API=false` and `VITE_API_BASE_URL=http://localhost:8080`.
- Browser smoke in backend mode:
  - Login as manager.
  - Open `/manager/shift-types`.
  - Confirm list loads from backend.
  - Create one shift type.
  - Edit it.
  - Toggle status.
  - Delete it or confirm backend dependency error is shown clearly.

## Acceptance Criteria

- `ShiftTypesPage` works in mock mode with no behavioral regression.
- `ShiftTypesPage` works against backend in backend mode.
- Backend DTOs do not leak into React page components.
- No Work Shift UI is added in this slice.
- Build verification passes in both default and backend modes.

## Spec Self-Review

- Placeholder scan: no placeholders remain.
- Internal consistency: scope, architecture, and acceptance criteria all target only shift type integration.
- Scope check: focused enough for one implementation plan.
- Ambiguity check: Work Shift and schedule integration are explicitly excluded.

# One-Shot Schedule Generation Design

## Goal

Support the product flow: choose employees, choose shift types, choose a date range, then let backend create the period, create work shifts, and run OR-Tools optimization in one action.

## Backend API

- Add `POST /api/admin/schedule-periods/generate`.
- Request includes `periodName`, `startDate`, `endDate`, `versionNo`, `employeeIds`, and `shiftTypeIds`.
- The first implementation validates `employeeIds` are present but uses the existing optimizer's active-employee payload behavior; employee scoping can become strict in a later slice.
- For each date in the period and each selected active/applicable shift type, create one `WorkShift`.
- Use each shift type's `minStaff` as `requiredStaff`.
- Call existing optimization after shifts are created.
- Response contains `periodId`, `periodName`, `status`, `aiStatus`, `solverStatus`, `assignmentCount`, and `warnings`.

## Frontend Behavior

- Backend mode shows real active employees and shift types, not mock seeds.
- Submit calls the one-shot generation endpoint.
- The page shows optimize/generation result and a detail link.
- Mock mode remains unchanged.

## Error Handling

- Invalid empty employee/shift selections return a backend validation error.
- Generation errors render as red notices.

## Testing

- Add backend service/controller-level coverage for request orchestration.
- Add frontend service tests for the new endpoint.
- Run focused backend and frontend checks, build, and lint.

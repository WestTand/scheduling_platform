# Phase 9E Scheduling Rules Backend Integration Design

## Context

Phase 9C connected manager employee/account management to the backend. Phase 9D connected manager shift types to the backend. The next useful Phase 9 slice is scheduling rules because these rules are required input for schedule generation and AI optimization.

The backend already exposes scheduling rule APIs:

- `GET /api/admin/scheduling-rules`
- `PUT /api/admin/scheduling-rules`

The frontend currently has no dedicated Scheduling Rules page or service. This slice adds a focused manager page and connects it to the backend while preserving mock mode.

## Goal

Add a manager-facing `Quy định xếp lịch` page that can load and save scheduling rules from the backend when `VITE_USE_MOCK_API=false`.

## Scope

Included:

- Add frontend backend DTO types for scheduling rules.
- Add adapter functions between backend scheduling rule DTOs and frontend UI form state.
- Add `backendSchedulingRuleService` for `GET` and `PUT /api/admin/scheduling-rules`.
- Add public `schedulingRuleService` with mock/backend switching via `apiConfig.useMockApi`.
- Add `SchedulingRulesPage` under manager pages.
- Add route `/manager/scheduling-rules` guarded for manager role.
- Add sidebar navigation item for managers.
- Verify backend-mode API access and frontend builds.

Excluded:

- No schedule generation integration changes.
- No work shift, assignment, or AI payload changes.
- No backend contract redesign unless a verified contract bug blocks the page.
- No large UI redesign of layout/sidebar.

## Backend Contract

Response and update request share this shape:

```ts
interface SchedulingRulesDto {
  hardRules: {
    minRestHoursBetweenShifts: number
    maxWorkHoursPerDay: number
    maxShiftsPerWeek: number
  }
  quotas: {
    overtimeShiftsPerWeek: number
    bathingShiftsPerWeek: number
    onCallEveryNDays: number
  }
  softWeights: {
    fairness: number | string
    preference: number | string
    weekend: number | string
    heavyShift: number | string
    workHourBalance: number | string
  }
}
```

Backend validation ranges:

- `minRestHoursBetweenShifts`: 0-24.
- `maxWorkHoursPerDay`: 1-24.
- `maxShiftsPerWeek`: 0-14.
- `overtimeShiftsPerWeek`: 0-14.
- `bathingShiftsPerWeek`: 0-14.
- `onCallEveryNDays`: 1-31.
- Soft weights: `0.00` to `999.99`, up to two decimal places.

## UI Design

The page should use existing manager page patterns:

- `PageHeader` with title `Quy định xếp lịch`.
- Loading, error, and retry states.
- A form with three sections:
  - `Quy định bắt buộc` for hard rules.
  - `Hạn mức ca theo tuần` for quotas.
  - `Trọng số ưu tiên` for soft weights.
- Save button submits the full rules object with one `PUT`.
- Reset button restores current loaded backend values without calling backend.

Important labels:

- `overtimeShiftsPerWeek`: `Số ngày ngoài giờ tối đa / tuần`.
- `bathingShiftsPerWeek`: `Số ngày thông tầm tối đa / tuần`.
- `onCallEveryNDays`: `Khoảng cách tối thiểu giữa ngày trực`.

The frontend should avoid saying these quota fields are shift rows. They represent scheduling quota counts used by backend rules and AI.

## Architecture

Use the same frontend integration pattern as Employees and Shift Types:

- Page components consume UI-friendly frontend models only.
- Backend DTOs stay inside `types/backendSchedulingRule.ts`, adapters, and backend service.
- Public `schedulingRuleService` selects mock or backend mode.
- Mock mode returns a realistic default rules object and supports local updates.

Planned units:

- `frontend/src/types/schedulingRule.ts`: frontend UI model and form value types.
- `frontend/src/types/backendSchedulingRule.ts`: backend DTO shape.
- `frontend/src/adapters/schedulingRuleAdapter.ts`: backend DTO to frontend model and frontend form to backend request.
- `frontend/src/services/backendSchedulingRuleService.ts`: authenticated backend calls.
- `frontend/src/services/schedulingRuleService.ts`: public mock/backend-aware service.
- `frontend/src/pages/manager/SchedulingRulesPage.tsx`: UI and form.
- `frontend/src/routes/AppRoutes.tsx`: manager route.
- `frontend/src/components/layout/MainLayout.tsx` or nav config owner if route items live there: add sidebar entry.
- `frontend/src/components/layout/Sidebar.tsx`: only add icon mapping if the new item needs a new icon.

## Data Flow

Load flow:

1. `SchedulingRulesPage` mounts.
2. Page calls `schedulingRuleService.getRules()`.
3. Service chooses mock data or backend `GET /api/admin/scheduling-rules`.
4. Adapter normalizes number/string decimal fields into form-safe numeric values.
5. Page renders the form.

Save flow:

1. User edits fields.
2. Page validates client-side ranges matching backend validation.
3. Page calls `schedulingRuleService.updateRules(formState)`.
4. Backend mode sends the full request to `PUT /api/admin/scheduling-rules`.
5. Page updates the loaded snapshot and shows success text.

## Error Handling

- Missing/invalid form values block submission with a page-level form error.
- Backend errors surface through the existing `apiClient` message parsing.
- Failed load shows `ErrorState` with retry.
- Failed save keeps the dialog/page open and keeps user edits intact.

## Testing

- Frontend default build must pass.
- Frontend backend-mode build must pass.
- If backend code changes are needed, add focused backend tests first.
- Browser smoke in backend mode:
  - Login as manager.
  - Open `/manager/scheduling-rules`.
  - Confirm rules load.
  - Change one non-destructive field such as `fairness`.
  - Save and confirm reload reflects the saved value.
  - Reset the value if needed to avoid leaving surprising local dev data.

## Acceptance Criteria

- Manager can open `/manager/scheduling-rules` from sidebar.
- Page loads current scheduling rules in backend mode.
- Page saves full scheduling rules in backend mode.
- Mock mode still works without backend.
- Backend DTOs do not leak into React page code.
- Scheduling, assignment, and AI pages are unchanged in this slice.

## Spec Self-Review

- Placeholder scan: no placeholders remain.
- Internal consistency: the scope, architecture, and acceptance criteria all target only scheduling rules integration.
- Scope check: focused enough for one implementation plan.
- Ambiguity check: schedule generation and assignment integration are explicitly excluded.

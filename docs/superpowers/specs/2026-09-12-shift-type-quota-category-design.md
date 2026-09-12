# Shift Type Quota Category Design

## Context

The backend-AI scheduling contract needs each shift type to expose a stable `quotaCategory` so OR-Tools can enforce special quotas without guessing from display names. Current `ShiftType` has `shiftName`, `overnight`, `heavy`, staff bounds, and `status`, but no stable category for overtime, bathing, or on-call quotas.

## Scope

Add `quotaCategory` to Shift Type management APIs and entity mapping.

Affected APIs:

- `GET /api/admin/shift-types?query=&status=`
- `POST /api/admin/shift-types`
- `PUT /api/admin/shift-types/{shiftTypeId}`

No AI-service code changes are included in this backend slice.

## Data Model

Add a nullable/string column-style entity field:

- `quotaCategory`: `REGULAR`, `OVERTIME`, `BATHING`, or `ON_CALL`.

Default value is `REGULAR`.

Rationale:

- `REGULAR` is explicit and easier for API consumers than `null`.
- Backend can map `REGULAR` to AI payload `quotaCategory = null` later, while mapping the other values directly.
- We avoid inferring quota behavior from Vietnamese shift names such as `Ngoai gio`, `Thong tam`, or `Truc`.

## API Behavior

### Create Shift Type

`quotaCategory` is optional. If omitted or blank, service stores `REGULAR`.

Valid values:

- `REGULAR`
- `OVERTIME`
- `BATHING`
- `ON_CALL`

Invalid values return `400 BAD_REQUEST` with `Invalid shift type request`.

### Update Shift Type

`quotaCategory` is required and must be one of the valid values.

### List/Response

`AdminShiftTypeResponse` includes `quotaCategory` so the frontend/backend-AI mapper can read it.

## Backend-AI Mapping

When building the AI request later:

- Backend `REGULAR` -> AI `quotaCategory: null`
- Backend `OVERTIME` -> AI `quotaCategory: "OVERTIME"`
- Backend `BATHING` -> AI `quotaCategory: "BATHING"`
- Backend `ON_CALL` -> AI `quotaCategory: "ON_CALL"`

This keeps the backend API explicit while preserving the AI-service schema from PR #11.

## Validation

Use Bean Validation where possible:

- Create request: `quotaCategory` can be null/blank, but if provided must be max 20 characters.
- Update request: `quotaCategory` must be not blank, max 20 characters, and one of the allowed values.

Use service validation to normalize and reject invalid values.

## Testing

Add/update tests for:

- Create defaults omitted quota category to `REGULAR`.
- Create accepts `OVERTIME` and returns it.
- Update changes quota category.
- Update rejects invalid quota category.
- Response DTO includes quota category.
- Full backend test suite passes.

## Docs

Update `docs/backend-ai-scheduling-contract.md` to state backend stores `REGULAR` but sends `null` to AI for regular shift types.

# Admin Shift Type APIs Design

## Context

Phase 3 Admin Core already includes employee administration and account administration. The next required admin-core slice is shift type management. `ShiftType` already exists as an entity and is the source configuration for later `WorkShift` generation, manual scheduling, employee preferences, and AI scheduling.

This feature adds backend APIs for manager/admin users to list, create, update, lock, and unlock shift types.

## Scope

Implement backend APIs under `/api/admin/shift-types`:

- `GET /api/admin/shift-types?query=&status=`
- `POST /api/admin/shift-types`
- `PUT /api/admin/shift-types/{shiftTypeId}`
- `POST /api/admin/shift-types/{shiftTypeId}/lock`
- `POST /api/admin/shift-types/{shiftTypeId}/unlock`

All endpoints are available to `MANAGER` and `ADMIN`. `EMPLOYEE` must receive `403`.

This slice does not include creating `WorkShift`, generating schedules, deleting shift types, or frontend screens.

## API Behavior

### List Shift Types

`GET /api/admin/shift-types` returns shift types with optional filters:

- `query`: trim whitespace and match `shiftName` case-insensitively.
- `status`: optional string filter. Supported values are `ACTIVE` and `INACTIVE`.

Response fields:

- id
- shiftName
- startTime
- endTime
- durationHours
- overnight
- heavy
- minStaff
- maxStaff
- status

### Create Shift Type

`POST /api/admin/shift-types` creates a new active shift type.

Request fields:

- `shiftName`: required, not blank, max 50 characters, unique.
- `startTime`: required, `HH:mm:ss` JSON time format accepted by Spring.
- `endTime`: required, `HH:mm:ss` JSON time format accepted by Spring.
- `durationHours`: required, positive number.
- `overnight`: optional boolean, defaults to `false` when omitted.
- `heavy`: optional boolean, defaults to `false` when omitted.
- `minStaff`: required, at least `1`.
- `maxStaff`: required, at least `minStaff`.

Create returns `201 Created` with the created response DTO.

Duplicate `shiftName` returns `409 CONFLICT` through existing `DuplicateResourceException`.

### Update Shift Type

`PUT /api/admin/shift-types/{shiftTypeId}` updates editable fields on an existing shift type:

- `shiftName`
- `startTime`
- `endTime`
- `durationHours`
- `overnight`
- `heavy`
- `minStaff`
- `maxStaff`
- `status`

Missing shift type returns `404 NOT_FOUND` through a new `ShiftTypeNotFoundException` handled by the global exception handler.

If the new `shiftName` belongs to another shift type, return `409 CONFLICT`.

### Lock/Unlock Shift Type

`POST /api/admin/shift-types/{shiftTypeId}/lock` sets `status=INACTIVE`.

`POST /api/admin/shift-types/{shiftTypeId}/unlock` sets `status=ACTIVE`.

Locking does not delete or detach historical data. Existing `WorkShift`, `Preference`, and future assignments remain intact. Inactive shift types should be excluded by future create-work-shift / AI inputs unless explicitly requested.

## Components

Create a module parallel to existing admin modules:

- `ShiftTypeRepository`: Spring Data repository with optional search query and duplicate-name helpers.
- `AdminShiftTypeService`: list, create, update, lock, unlock business logic and validation.
- `AdminShiftTypeController`: HTTP endpoints and role protection.
- DTOs under `scheduling.scheduling.admin.dto`:
  - `AdminShiftTypeResponse`
  - `CreateShiftTypeRequest`
  - `UpdateShiftTypeRequest`
- `ShiftTypeNotFoundException`: thrown when a shift type id does not exist.

## Validation Rules

Use Bean Validation for simple field validation:

- `@NotBlank`, `@Size(max = 50)` for `shiftName`.
- `@NotNull` for time, duration, minStaff, maxStaff.
- `@DecimalMin(value = "0.1")` for `durationHours`.
- `@Min(1)` for `minStaff` and `maxStaff`.

Use service validation for cross-field rules:

- `maxStaff >= minStaff`.
- duplicate `shiftName` checks.

Invalid cross-field validation returns `400 BAD_REQUEST` using `ResponseStatusException` with message `Invalid shift type request`.

## Data Flow

List flow:

1. Controller accepts optional `query` and `status`.
2. Service normalizes blank values to `null`.
3. Repository returns matching shift types.
4. Service maps entities to response DTOs.

Create/update flow:

1. Controller validates basic request constraints.
2. Service validates `maxStaff >= minStaff` and unique name.
3. Service creates or updates entity fields.
4. Repository persists entity.
5. Service returns response DTO.

Lock/unlock flow:

1. Controller receives shift type id.
2. Service loads entity or throws `ShiftTypeNotFoundException`.
3. Service changes `status` to `INACTIVE` or `ACTIVE`.
4. Repository saves entity.

## Testing

Add service and controller tests covering:

- List all shift types.
- List with query/status filters.
- Create valid shift type with default boolean values.
- Create rejects duplicate name.
- Create/update reject `maxStaff < minStaff`.
- Update existing shift type fields.
- Update rejects duplicate name owned by another shift type.
- Lock sets status `INACTIVE`.
- Unlock sets status `ACTIVE`.
- Missing id throws `ShiftTypeNotFoundException` and maps to `404`.
- Controller allows `MANAGER` / `ADMIN` and rejects `EMPLOYEE`.

Run full backend verification with `mvnw.cmd clean test` on Windows.

## Notes

No delete endpoint is included. Shift types are referenced by future preferences and work shifts, so soft lock via `status=INACTIVE` is safer and preserves historical scheduling data.

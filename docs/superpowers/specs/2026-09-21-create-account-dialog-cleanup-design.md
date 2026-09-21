# Create Account Dialog Cleanup Design

## Context

Phase 9C changed backend manager account creation to use an existing employee and a username:

```json
{
  "employeeId": 123,
  "username": "employee123"
}
```

The frontend create-account dialog still shows mock-era fields for email and temporary password. These fields are no longer used by the backend and make the UI confusing.

## Goal

Make the create-account dialog match the backend account creation contract.

## Design

The dialog should keep:

- Employee selector.
- Read-only employee code.
- Read-only employee full name.
- Read-only employee email as informational text.
- Username input.
- Role summary: `Nhân viên`.

The dialog should remove:

- Editable email input.
- Temporary password input.
- Confirm temporary password input.
- Password validation.
- Email validation that blocks account creation beyond checking the selected employee has an email.

The submit payload should include only:

```ts
{
  employeeId: string
  username: string
}
```

Mock mode should continue working by generating/storing its mock temporary password internally.

## Testing

- `npm.cmd run build` must pass.
- Backend-mode employee account creation should still call `POST /api/manager/accounts` with `employeeId` and `username` only.
- UI should no longer display unused password fields.

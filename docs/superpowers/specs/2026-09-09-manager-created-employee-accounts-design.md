# Manager-Created Employee Accounts Design

## Scope

This change adds the account role needed for system-level administration and introduces the API shape for managers to create employee accounts. It replaces public self-registration with manager-driven account creation.

## Roles

Accounts have three roles:

- `ADMIN`: system-level administrator. This account is not tied to an employee record.
- `MANAGER`: operational manager. Managers can create employee accounts.
- `EMPLOYEE`: normal employee account.

`ADMIN` and `MANAGER` can create employee accounts. The create-account endpoint always creates `EMPLOYEE` accounts only.

## Endpoint

Use `POST /api/manager/accounts` for employee account creation.

The endpoint requires an authenticated account with role `MANAGER` or `ADMIN`.

Request body:

```json
{
  "username": "employee01",
  "employeeCode": "EMP001",
  "fullName": "Nguyen Van A",
  "email": "employee01@gmail.com",
  "phone": "0900000001"
}
```

The client does not send a password. The backend assigns the default password from the `DEFAULT_EMPLOYEE_PASSWORD` environment variable and stores only the encoded password hash.

## Created Account State

The created account has:

- `role = EMPLOYEE`
- `isActive = true`
- `status = PENDING_ACTIVATION`
- a linked `Employee` record with the provided employee profile fields

The account cannot complete the normal login flow until the activation steps are implemented in later phases.

## Validation And Errors

The service rejects duplicate usernames and duplicate employee codes.

Only authenticated `ADMIN` or `MANAGER` accounts can call the endpoint. Unauthorized or insufficient-role callers receive the standard Spring Security error response.

## Deferred Work

OTP generation, Gmail delivery, OTP verification, and forced password change are intentionally deferred to later phases so each change remains small and reviewable.

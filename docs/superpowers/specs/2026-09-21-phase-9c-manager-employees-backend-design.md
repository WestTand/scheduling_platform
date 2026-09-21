# Phase 9C Manager Employees Backend Integration Design

## Context

Phase 9B completed the backend auth lifecycle: login, activation OTP, forced password change, CORS, and self-service OTP resend. The manager employees page still uses frontend mock services for employee and account data.

Backend already has partial support:

- `GET /api/admin/employees`
- `GET /api/admin/employees/{employeeId}`
- `PUT /api/admin/employees/{employeeId}`
- `POST /api/admin/employees/{employeeId}/lock`
- `POST /api/admin/employees/{employeeId}/unlock`
- `POST /api/manager/accounts`
- `POST /api/manager/accounts/{accountId}/activation/resend`
- `POST /api/manager/accounts/{accountId}/password/reset`

The gap is that frontend `EmployeesPage` expects to create employees separately, then optionally create an account for an existing employee. Current `ManagerAccountService.createEmployeeAccount` creates both employee and account together, which does not match the UI flow.

## Goal

Migrate `frontend/src/pages/manager/EmployeesPage.tsx` to use backend data and manager APIs in backend mode, while keeping mock mode working.

## Non-Goals

- Do not migrate scheduling pages or employee dashboard pages.
- Do not implement hard-delete for employees.
- Do not remove mock mode.
- Do not change employee login to email-based lookup.
- Do not redesign the employees page layout.

## Backend Design

### Create Employee

Add:

```http
POST /api/admin/employees
```

Request body:

```json
{
  "employeeCode": "EMP123",
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "phone": "0900000000",
  "status": "ACTIVE"
}
```

Response:

```http
201 Created
```

Body: `AdminEmployeeResponse`.

Validation and behavior:

- Require manager or admin.
- Reject duplicate `employeeCode`.
- Store employee status from request, defaulting to `ACTIVE` only if the request DTO intentionally omits it.
- Record activity log using the same style as update/lock/unlock.

### Employee Update And Soft Delete

Keep existing endpoints:

```http
PUT /api/admin/employees/{employeeId}
POST /api/admin/employees/{employeeId}/lock
POST /api/admin/employees/{employeeId}/unlock
```

Delete from the UI maps to soft-delete:

- Set employee `status = INACTIVE`.
- Set linked account `isActive = false`.
- If linked account status is `ACTIVE`, set it to `INACTIVE`.
- Revoke refresh tokens for the account.
- Do not remove employee or account rows from the database.

The existing `lock` endpoint already models these semantics and should be reused for backend-mode delete unless tests reveal missing behavior.

### Account Creation For Existing Employee

Refactor manager account creation so the UI can create an account for an existing employee.

Preferred endpoint:

```http
POST /api/manager/accounts
```

Request body:

```json
{
  "employeeId": 123,
  "username": "employee123"
}
```

Behavior:

- Require manager or admin.
- Find existing employee by `employeeId`.
- Reject non-employee role if the backend has an employee role concept available on the employee record.
- Reject employees that already have an account.
- Reject duplicate username.
- Use `app.auth.default-employee-password` as the temporary password.
- Create `Account` with role `EMPLOYEE`, `isActive = true`, `status = PENDING_ACTIVATION`.
- Send activation OTP using existing OTP service.
- Record activity log.
- Return `AccountResponse`.

The old request shape that created employee and account together is not preserved unless a concrete existing consumer is found. This endpoint has not been used reliably by the frontend yet, so a clean contract is preferred.

### Account Resend And Reset

Keep existing endpoints:

```http
POST /api/manager/accounts/{accountId}/activation/resend
POST /api/manager/accounts/{accountId}/password/reset
```

Frontend backend mode will call these endpoints only for accounts known from backend employee responses.

### Employee Response Account Summary

`AdminEmployeeResponse` should include account summary data if it does not already provide enough information for the frontend:

- account id
- username
- role
- status
- created at
- activated at
- last login at

This lets the frontend derive account status without a separate account list endpoint.

### Activity Log Bug

Previous manual testing showed account creation could send email but then roll back with a database error similar to:

```text
ERROR: function lower(bytea) does not exist
```

Phase 9C must reproduce account creation through tests or local smoke testing. If the activity log JSON binding issue still exists, fix it in this phase before considering account creation done.

## Frontend Design

### Services

Keep the current service boundaries and make them backend-aware.

`employeeService`:

- Mock mode keeps current in-memory behavior.
- Backend mode calls admin employee endpoints.
- Backend mode performs client-side pagination because backend currently returns a list.
- `remove(id)` maps to backend `lock` soft-delete.
- `toggleStatus(id)` maps active employees to `lock` and inactive employees to `unlock`.

`accountService` manager methods:

- Mock auth lifecycle methods remain unchanged.
- Backend mode `createEmployeeAccount` calls the manager account endpoint with `employeeId` and `username`.
- Backend mode `resendActivationOtp` calls manager resend endpoint.
- Backend mode `resetPassword` calls manager reset endpoint if/when surfaced by the UI.

Add backend adapters as needed:

- `backendEmployeeService`
- `backendManagerAccountService`
- DTO adapters from backend employee/account responses to frontend `Employee` and `UserAccount` types.

### Employees Page

Keep the page layout and dialogs mostly unchanged.

Changes:

- Load employees from `employeeService.list`.
- Derive account rows from backend employee response account summary in backend mode.
- After create/update/lock/unlock/create-account/resend, refresh data.
- Backend-mode OTP resend success message must not mention mock OTP `123456`.
- Delete button remains visible but performs soft-delete. If wording becomes confusing, the confirmation text should say the employee will be deactivated, not permanently deleted.

### Data Mapping

Frontend `Employee.id` and `UserAccount.id` remain strings. Backend numeric ids are converted with `String(id)` at the adapter boundary.

Frontend role handling:

- Existing UI form has `role`, but backend employee entity currently does not clearly store manager-vs-employee role on employee records.
- Phase 9C should not invent new backend role semantics for employees.
- In backend mode, new employee creation should treat employees as operational employees. Account creation will create `EMPLOYEE` accounts only.
- Existing manager accounts are handled by auth/account records, not by creating manager employees from this page.

## Testing

Backend tests:

- `POST /api/admin/employees` creates an employee.
- Duplicate employee code is rejected.
- `PUT /api/admin/employees/{id}` updates employee fields.
- `lock` performs soft-delete semantics and revokes tokens.
- `unlock` reactivates employee/account according to existing behavior.
- `POST /api/manager/accounts` creates account for an existing employee and sends OTP.
- Account creation rejects duplicate username.
- Account creation rejects employee that already has an account.

Frontend verification:

- `npm.cmd run build` passes.
- Backend mode manager employee list loads.
- Add employee works.
- Create account works and sends OTP.
- Resend OTP works and shows backend-appropriate success text.
- Soft-delete deactivates the employee instead of removing rows.

## Risks

- Backend list endpoint is not paginated. Client-side pagination is acceptable for Phase 9C but may need server-side pagination later.
- Changing `POST /api/manager/accounts` request shape can break unknown external consumers. No concrete external consumer is known in this project; frontend integration benefits from the cleaner contract.
- Account creation touches email sending and activity logging in one transaction. Existing rollback behavior must be tested before merging.
- The frontend form still exposes a role field from mock-era data. Backend mode should avoid promising manager account creation from this page unless a later phase explicitly adds it.

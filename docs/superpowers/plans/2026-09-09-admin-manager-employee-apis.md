# Admin/Manager Employee APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Phase 3 admin core slice: manager/admin employee listing, detail, search, update, lock, and unlock APIs.

**Architecture:** Add an `admin` API package for employee management and keep account lifecycle mutations in service code. Reuse existing `Employee`, `Account`, `EmployeeRepository`, `AccountRepository`, `RefreshTokenService`, and Spring Security role checks.

**Tech Stack:** Spring Boot 4.1, Spring MVC, Spring Security `@PreAuthorize`, Spring Data JPA, Jakarta Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## Scope

This is Phase 3 slice `01 Admin/Manager Employee APIs`.

Implemented endpoints:

- `GET /api/admin/employees?query=&status=` lists employees with optional search/filter.
- `GET /api/admin/employees/{employeeId}` returns one employee with linked account summary when present.
- `PUT /api/admin/employees/{employeeId}` updates editable profile fields.
- `POST /api/admin/employees/{employeeId}/lock` sets employee status inactive, locks linked account, revokes refresh tokens.
- `POST /api/admin/employees/{employeeId}/unlock` sets employee status active and unlocks linked account without changing lifecycle status.

Authorization:

- `MANAGER` and `ADMIN` can call all endpoints.
- `EMPLOYEE` receives `403`.

## File Structure

- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeController.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminEmployeeResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminEmployeeAccountResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateEmployeeRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/EmployeeNotFoundException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeControllerTest.java`

## API Contract

### Employee Response

```json
{
  "id": 10,
  "employeeCode": "EMP001",
  "fullName": "Nguyen Van A",
  "email": "employee01@example.com",
  "phone": "0900000001",
  "status": "ACTIVE",
  "account": {
    "id": 20,
    "username": "employee01",
    "role": "EMPLOYEE",
    "status": "ACTIVE",
    "active": true
  }
}
```

### Update Request

```json
{
  "fullName": "Nguyen Van A Updated",
  "email": "employee01.updated@example.com",
  "phone": "0900000002",
  "status": "ACTIVE"
}
```

Validation:

- `fullName`: required, max 100.
- `email`: optional, valid email, max 100.
- `phone`: optional, max 20.
- `status`: required, allowed `ACTIVE` or `INACTIVE`.

## Task 1: List/Search Employees

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminEmployeeResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminEmployeeAccountResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`

Steps:

- [ ] Write failing service test: `listEmployeesReturnsAllEmployeesWhenNoFilters`.
- [ ] Write failing service test: `listEmployeesSearchesCodeOrNameAndFiltersStatus`.
- [ ] Add repository methods `findAll()` usage and a custom `@Query` method `search(String query, String status)`.
- [ ] Implement response DTO mapping from `Employee` and optional linked `Account`.
- [ ] Implement `AdminEmployeeService.listEmployees(String query, String status)`.
- [ ] Run `./mvnw.cmd "-Dtest=AdminEmployeeServiceTest" test`.
- [ ] Commit `feat: list admin employees`.

## Task 2: Employee Detail And Update

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateEmployeeRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/EmployeeNotFoundException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`

Steps:

- [ ] Write failing service test: `getEmployeeReturnsEmployeeWithAccountSummary`.
- [ ] Write failing service test: `getEmployeeRejectsMissingEmployee`.
- [ ] Write failing service test: `updateEmployeeChangesEditableFields`.
- [ ] Write failing service test: `updateEmployeeRejectsMissingEmployee`.
- [ ] Implement `EmployeeNotFoundException` with message `Employee not found`.
- [ ] Map `EmployeeNotFoundException` to `404 EMPLOYEE_NOT_FOUND`.
- [ ] Implement `AdminEmployeeService.getEmployee(Long employeeId)`.
- [ ] Implement `AdminEmployeeService.updateEmployee(Long employeeId, UpdateEmployeeRequest request)`.
- [ ] Run `./mvnw.cmd "-Dtest=AdminEmployeeServiceTest" test`.
- [ ] Commit `feat: manage admin employee profiles`.

## Task 3: Lock And Unlock Employee

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`

Steps:

- [ ] Write failing service test: `lockEmployeeMarksEmployeeInactiveLocksAccountAndRevokesTokens`.
- [ ] Write failing service test: `unlockEmployeeMarksEmployeeActiveAndUnlocksAccount`.
- [ ] Implement `AdminEmployeeService.lockEmployee(Long employeeId)`.
- [ ] Implement `AdminEmployeeService.unlockEmployee(Long employeeId)`.
- [ ] Lock behavior: employee `status = INACTIVE`, linked account `isActive = false`, linked account `status = INACTIVE`, revoke refresh tokens by account id.
- [ ] Unlock behavior: employee `status = ACTIVE`, linked account `isActive = true`; keep account lifecycle status unchanged unless it is `INACTIVE`, then set `ACTIVE`.
- [ ] Run `./mvnw.cmd "-Dtest=AdminEmployeeServiceTest" test`.
- [ ] Commit `feat: lock and unlock employees`.

## Task 4: Controller Endpoints And Authorization

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeControllerTest.java`

Steps:

- [ ] Write failing controller test: manager can list employees.
- [ ] Write failing controller test: manager can get employee detail.
- [ ] Write failing controller test: manager can update employee.
- [ ] Write failing controller test: manager can lock employee.
- [ ] Write failing controller test: manager can unlock employee.
- [ ] Write failing controller test: employee cannot access admin employee APIs.
- [ ] Implement controller under `/api/admin/employees` with `@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")` on all methods.
- [ ] Return `200 OK` for list/detail/update and `204 No Content` for lock/unlock.
- [ ] Run `./mvnw.cmd "-Dtest=AdminEmployeeControllerTest" test`.
- [ ] Commit `feat: expose admin employee APIs`.

## Task 5: Verification And Project Plan Update

**Files:**

- Modify: `docs/project_plan.md`

Steps:

- [ ] Mark completed Phase 3 items for employee list, search, update, lock/unlock, and tests.
- [ ] Run `./mvnw.cmd clean test` from `backend`.
- [ ] Run repo-wide search for forbidden default-password literals.
- [ ] Run `git diff --check`.
- [ ] Commit `docs: update admin employee api progress`.
- [ ] Push `main`.

## Self-Review

- Spec coverage: plan covers the first Phase 3 employee API slice and does not include ShiftType or scheduling rules.
- Placeholder scan: no `TODO`, `TBD`, or incomplete endpoint behavior remains.
- Type consistency: endpoint paths, DTO names, service method names, and exception code names are consistent across tasks.

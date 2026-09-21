# Create Account Dialog Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unused email and temporary-password fields from the manager create-account dialog.

**Architecture:** Keep backend contract unchanged and align the frontend form with `POST /api/manager/accounts`, which only needs an existing employee id and username. Mock account creation will keep using an internal default temporary password.

**Tech Stack:** React, TypeScript, Vite.

---

### Task 1: Simplify Account Creation Input

**Files:**
- Modify: `frontend/src/types/account.ts`
- Modify: `frontend/src/services/accountService.ts`
- Modify: `frontend/src/pages/manager/EmployeesPage.tsx`
- Modify: `frontend/src/components/account/CreateAccountDialog.tsx`

- [ ] **Step 1: Update account input type**

In `frontend/src/types/account.ts`, replace `CreateEmployeeAccountInput` with:

```ts
export interface CreateEmployeeAccountInput {
  employeeId: string
  username: string
}
```

- [ ] **Step 2: Update mock account creation password**

In `frontend/src/services/accountService.ts`, inside `createEmployeeAccount`, replace all `input.email` references with `employee.email` and store a default mock temporary password:

```ts
const mockTemporaryPassword = 'Temp1234'
```

Use `mockTemporaryPassword` for password requirement validation and `accountSecrets[account.id]`.

- [ ] **Step 3: Update page handler signature**

In `frontend/src/pages/manager/EmployeesPage.tsx`, change `handleCreateAccountSubmit` parameter type to:

```ts
const handleCreateAccountSubmit = async (input: { employeeId: string; username: string }) => {
```

- [ ] **Step 4: Simplify dialog state and validation**

In `frontend/src/components/account/CreateAccountDialog.tsx`:

- Remove `email`, `temporaryPassword`, and `confirmPassword` from form state.
- Keep validation that selected employee has an email.
- Validate only `username` is present and has no whitespace.
- Submit only `{ employeeId: selectedEmployee.id, username }`.

The submit block should be:

```ts
setError('')
await onSubmit({
  employeeId: selectedEmployee.id,
  username,
})
```

- [ ] **Step 5: Remove unused fields from JSX**

In `CreateAccountDialog.tsx`, remove these input sections:

- Editable email input.
- Temporary password input.
- Confirm temporary password input.

Keep email as read-only informational text:

```tsx
<div className="space-y-1 md:col-span-2">
  <p className="text-sm font-medium text-slate-700">Email nhân viên</p>
  <p className="rounded-xl border border-slate-200 bg-slate-100 px-3 py-2.5 text-sm text-slate-600">{selectedEmployee.email}</p>
</div>
```

- [ ] **Step 6: Build**

Run:

```powershell
npm.cmd run build
```

Working directory: `frontend`.

Expected: build passes.

- [ ] **Step 7: Commit**

Run:

```powershell
git add frontend/src/types/account.ts frontend/src/services/accountService.ts frontend/src/pages/manager/EmployeesPage.tsx frontend/src/components/account/CreateAccountDialog.tsx
git commit -m "fix: simplify create account dialog"
```

## Self-Review Notes

- Spec coverage: removes unused fields, keeps username and employee id, preserves mock mode with internal default password.
- Placeholder scan: no unresolved placeholders.
- Type consistency: `CreateEmployeeAccountInput` now matches backend contract.

# Phase 9F Leave Requests Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect manager and employee leave request flows to backend APIs end-to-end while preserving mock mode.

**Architecture:** Add backend leave request DTOs, adapters, and a backend service; then extend the existing public `leaveRequestService` to switch between mock and backend. Keep React pages consuming existing frontend models and avoid leaking raw backend DTOs into components.

**Tech Stack:** React, TypeScript, Vite, Spring Boot backend APIs already implemented.

---

## File Structure

- Create `frontend/src/types/backendLeaveRequest.ts`: backend DTOs for manager and employee leave APIs.
- Create `frontend/src/adapters/leaveRequestAdapter.ts`: backend DTO to frontend `LeaveRequest` mapping plus date helpers.
- Create `frontend/src/services/backendLeaveRequestService.ts`: authenticated calls to manager/employee leave endpoints.
- Modify `frontend/src/types/management.ts`: allow `CANCELLED` leave status if not already present.
- Modify `frontend/src/components/common/StatusBadge.tsx`: render `CANCELLED` cleanly if not already supported.
- Modify `frontend/src/services/leaveRequestService.ts`: route public service to backend mode and add employee create/cancel methods.
- Modify `frontend/src/pages/manager/LeaveRequestsPage.tsx`: keep UI, call backend-aware service, support backend filters.
- Modify `frontend/src/pages/employee/EmployeeLeaveRequestsPage.tsx`: replace local seed state with async service-backed list/create/cancel.

### Task 1: Add Leave Request Backend Types And Adapters

**Files:**
- Create: `frontend/src/types/backendLeaveRequest.ts`
- Create: `frontend/src/adapters/leaveRequestAdapter.ts`
- Modify: `frontend/src/types/management.ts`
- Modify: `frontend/src/components/common/StatusBadge.tsx`

- [ ] **Step 1: Add `CANCELLED` leave status**

In `frontend/src/types/management.ts`, update `LeaveRequestStatus` to include:

```ts
  CANCELLED: 'CANCELLED',
```

- [ ] **Step 2: Ensure status badge supports cancelled**

In `frontend/src/components/common/StatusBadge.tsx`, add `CANCELLED` to the status config using Vietnamese label `Đã hủy` and a neutral/slate tone consistent with existing status styles.

- [ ] **Step 3: Create backend DTO types**

Create `frontend/src/types/backendLeaveRequest.ts`:

```ts
export type BackendLeaveRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendAdminLeaveRequestResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  startDate: string
  endDate: string
  reason: string
  status: BackendLeaveRequestStatus
  reviewNote: string | null
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
}

export interface BackendEmployeeLeaveRequestResponse {
  id: number
  startDate: string
  endDate: string
  reason: string
  status: BackendLeaveRequestStatus
}

export interface BackendCreateLeaveRequestRequest {
  startDate: string
  endDate: string
  reason: string
}

export interface BackendReviewLeaveRequestRequest {
  reviewNote?: string
}
```

- [ ] **Step 4: Create leave adapters**

Create `frontend/src/adapters/leaveRequestAdapter.ts`:

```ts
import type { BackendAdminLeaveRequestResponse, BackendEmployeeLeaveRequestResponse } from '@/types/backendLeaveRequest'
import type { LeaveRequest } from '@/types/management'

export const inclusiveDays = (startDate: string, endDate: string) => {
  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const diff = end.getTime() - start.getTime()
  return Math.max(1, Math.floor(diff / 86_400_000) + 1)
}

const employeeStatusNote = (status: BackendEmployeeLeaveRequestResponse['status']) => {
  if (status === 'PENDING') return 'Yêu cầu đang chờ quản lý duyệt.'
  if (status === 'APPROVED') return 'Yêu cầu đã được duyệt.'
  if (status === 'REJECTED') return 'Yêu cầu đã bị từ chối.'
  return 'Yêu cầu đã được hủy.'
}

export const adaptBackendAdminLeaveRequest = (request: BackendAdminLeaveRequestResponse): LeaveRequest => ({
  id: String(request.id),
  employeeName: request.employeeName,
  employeeEmail: '',
  startDate: request.startDate,
  endDate: request.endDate,
  totalDays: inclusiveDays(request.startDate, request.endDate),
  reason: request.reason,
  requestedAt: request.startDate,
  status: request.status,
  reviewedBy: request.reviewedByUsername ?? undefined,
  reviewedAt: request.reviewedAt ?? undefined,
  managerNote: request.reviewNote ?? undefined,
})

export const adaptBackendEmployeeLeaveRequest = (request: BackendEmployeeLeaveRequestResponse): LeaveRequest => ({
  id: String(request.id),
  employeeName: 'Bạn',
  employeeEmail: '',
  startDate: request.startDate,
  endDate: request.endDate,
  totalDays: inclusiveDays(request.startDate, request.endDate),
  reason: request.reason,
  requestedAt: request.startDate,
  status: request.status,
  managerNote: employeeStatusNote(request.status),
})
```

- [ ] **Step 5: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/types/management.ts frontend/src/components/common/StatusBadge.tsx frontend/src/types/backendLeaveRequest.ts frontend/src/adapters/leaveRequestAdapter.ts
git commit -m "feat: add leave request adapters"
```

### Task 2: Add Backend Leave Request Service

**Files:**
- Create: `frontend/src/services/backendLeaveRequestService.ts`

- [ ] **Step 1: Create backend service**

Create `frontend/src/services/backendLeaveRequestService.ts`:

```ts
import { adaptBackendAdminLeaveRequest, adaptBackendEmployeeLeaveRequest } from '@/adapters/leaveRequestAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type {
  BackendAdminLeaveRequestResponse,
  BackendCreateLeaveRequestRequest,
  BackendEmployeeLeaveRequestResponse,
} from '@/types/backendLeaveRequest'
import type { LeaveRequest, LeaveRequestDecision, PaginatedResult } from '@/types/management'

interface AdminListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
  startDate?: string
  endDate?: string
}

interface EmployeeListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
  startDate?: string
  endDate?: string
}

const paginate = (items: LeaveRequest[], page: number, pageSize: number): PaginatedResult<LeaveRequest> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterSearch = (items: LeaveRequest[], search: string) => {
  const normalized = search.trim().toLowerCase()
  if (!normalized) return items
  return items.filter((request) =>
    [request.employeeName, request.reason].some((value) => value.toLowerCase().includes(normalized)),
  )
}

const addDateParams = (params: URLSearchParams, startDate?: string, endDate?: string) => {
  if (startDate) params.set('from', startDate)
  if (endDate) params.set('to', endDate)
}

export const backendLeaveRequestService = {
  async listForManager({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: AdminListParams = {}) {
    const params = new URLSearchParams()
    if (status !== 'ALL') params.set('status', status)
    addDateParams(params, startDate, endDate)
    const query = params.toString()
    const response = await requestJson<BackendAdminLeaveRequestResponse[]>(`/api/admin/leave-requests${query ? `?${query}` : ''}`)
    return paginate(filterSearch(response.map(adaptBackendAdminLeaveRequest), search), page, pageSize)
  },

  async updateStatus(id: string, decision: LeaveRequestDecision) {
    const action = decision.status === 'APPROVED' ? 'approve' : 'reject'
    const response = await requestJson<BackendAdminLeaveRequestResponse>(`/api/admin/leave-requests/${encodeURIComponent(id)}/${action}`, {
      method: 'POST',
      body: { reviewNote: decision.managerNote.trim() || undefined },
    })
    return adaptBackendAdminLeaveRequest(response)
  },

  async listForEmployee({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: EmployeeListParams = {}) {
    const params = new URLSearchParams()
    if (status !== 'ALL') params.set('status', status)
    addDateParams(params, startDate, endDate)
    const query = params.toString()
    const response = await requestJson<BackendEmployeeLeaveRequestResponse[]>(`/api/employee/leave-requests${query ? `?${query}` : ''}`)
    return paginate(filterSearch(response.map(adaptBackendEmployeeLeaveRequest), search), page, pageSize)
  },

  async createForEmployee(input: BackendCreateLeaveRequestRequest) {
    const response = await requestJson<BackendEmployeeLeaveRequestResponse>('/api/employee/leave-requests', {
      method: 'POST',
      body: input,
    })
    return adaptBackendEmployeeLeaveRequest(response)
  },

  async cancelForEmployee(id: string) {
    await requestVoid(`/api/employee/leave-requests/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
  },
}
```

- [ ] **Step 2: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/services/backendLeaveRequestService.ts
git commit -m "feat: add backend leave request service"
```

### Task 3: Wire Public Leave Request Service

**Files:**
- Modify: `frontend/src/services/leaveRequestService.ts`

- [ ] **Step 1: Add imports**

Add imports:

```ts
import { apiConfig } from '@/config/api'
import { backendLeaveRequestService } from '@/services/backendLeaveRequestService'
```

- [ ] **Step 2: Route manager list and review to backend mode**

At the start of existing `list`, if backend mode, return:

```ts
backendLeaveRequestService.listForManager({ page, pageSize, search, status, startDate, endDate })
```

At the start of existing `updateStatus`, if backend mode, return:

```ts
backendLeaveRequestService.updateStatus(id, decision)
```

- [ ] **Step 3: Add employee list/create/cancel methods**

Add methods to `leaveRequestService`:

```ts
  async listMine(params: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string } = {}) {
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.listForEmployee(params)
    }
    return this.list(params)
  },

  async createMine(input: { startDate: string; endDate: string; reason: string; note?: string; type?: string }) {
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.createForEmployee({
        startDate: input.startDate,
        endDate: input.endDate,
        reason: input.reason.trim(),
      })
    }
    await delay(300)
    const newRequest: LeaveRequest = {
      id: `LEAVE-${Date.now()}`,
      employeeName: 'Bạn',
      employeeEmail: '',
      startDate: input.startDate,
      endDate: input.endDate,
      totalDays: Math.max(1, Math.floor((new Date(`${input.endDate}T00:00:00`).getTime() - new Date(`${input.startDate}T00:00:00`).getTime()) / 86_400_000) + 1),
      reason: input.reason.trim(),
      requestedAt: new Date().toISOString(),
      status: 'PENDING',
      managerNote: input.note || 'Yêu cầu đang chờ quản lý duyệt.',
    }
    leaveRequests = [newRequest, ...leaveRequests]
    return clone(newRequest)
  },

  async cancelMine(id: string) {
    if (!apiConfig.useMockApi) {
      await backendLeaveRequestService.cancelForEmployee(id)
      return
    }
    await delay(250)
    const request = leaveRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu nghỉ không tồn tại.')
    if (request.status !== 'PENDING') throw new Error('Chỉ có thể hủy yêu cầu đang chờ duyệt.')
    request.status = 'CANCELLED'
    request.managerNote = 'Nhân viên hủy yêu cầu.'
  },
```

- [ ] **Step 4: Build both modes**

Run from `frontend`:

```powershell
npm.cmd run build
```

Then:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: both builds pass.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/services/leaveRequestService.ts
git commit -m "feat: wire leave request service to backend"
```

### Task 4: Update Employee Leave Requests Page

**Files:**
- Modify: `frontend/src/pages/employee/EmployeeLeaveRequestsPage.tsx`

- [ ] **Step 1: Replace local seed state with service-backed state**

Modify the page to:

- Import `EmptyState`, `ErrorState`, `LoadingState`, `Pagination`, and `leaveRequestService`.
- Use `LeaveRequest` and `PaginatedResult` types from `@/types/management`.
- Keep `showForm`, `selected`, `search`, `statusFilter`, `form`, and `error`.
- Add `page`, `loading`, `loadError`, and `data` state.
- Load data with `leaveRequestService.listMine({ page, pageSize: 5, search, status: statusFilter })` in a mounted-guarded effect.

- [ ] **Step 2: Update create submit**

`handleSubmit` should validate date/reason, call:

```ts
await leaveRequestService.createMine({
  startDate: form.startDate,
  endDate: form.endDate,
  reason: form.reason,
  note: form.note,
  type: form.type,
})
```

Then close the form, reset input, clear error, set page to 1, and reload list.

- [ ] **Step 3: Update cancel**

`handleCancel` should call:

```ts
await leaveRequestService.cancelMine(id)
```

Then reload list. On error, alert backend message.

- [ ] **Step 4: Update render states and table data**

Use `data.data` instead of `filtered`. Add:

- loading state: `LoadingState label="Đang tải yêu cầu nghỉ..."`.
- error state: `ErrorState message={loadError} onRetry={fetchData}`.
- empty state: `EmptyState title="Không có yêu cầu nghỉ" description="Bạn chưa có yêu cầu nào phù hợp với bộ lọc."`.
- pagination: `<Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />`.

Remove `useMemo` filtering and local seed array usage.

- [ ] **Step 5: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/pages/employee/EmployeeLeaveRequestsPage.tsx
git commit -m "feat: connect employee leave requests to service"
```

### Task 5: Verify Manager Page And Final Checks

**Files:**
- Modify: `frontend/src/pages/manager/LeaveRequestsPage.tsx` only if backend-mode behavior requires small copy/type fixes.

- [ ] **Step 1: Review manager page integration**

Confirm `LeaveRequestsPage` still works with `leaveRequestService.list` and `updateStatus`. If needed, adjust only small UI copy for `CANCELLED` or backend field behavior.

- [ ] **Step 2: Verify backend APIs with fresh tokens**

Run manager API smoke from repo root:

```powershell
$body = @{ username = 'tx-test-manager'; password = '1111@' } | ConvertTo-Json; $login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body; $token = $login.accessToken; curl.exe -s -i "http://localhost:8080/api/admin/leave-requests" -H "Authorization: Bearer $token"
```

Expected: HTTP 200.

Run employee API smoke if employee account is active:

```powershell
$body = @{ username = 'phat143'; password = '1111@' } | ConvertTo-Json; $login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body; $token = $login.accessToken; curl.exe -s -i "http://localhost:8080/api/employee/leave-requests" -H "Authorization: Bearer $token"
```

Expected: HTTP 200, or document if the local employee account lifecycle blocks login.

- [ ] **Step 3: Run final frontend builds**

Run from repo root:

```powershell
git diff --check
```

Run from `frontend`:

```powershell
npm.cmd run build
```

Run backend-mode build:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: no diff-check output and both builds pass.

- [ ] **Step 4: Commit manager page fix if any**

If Step 1 required changes:

```powershell
git add frontend/src/pages/manager/LeaveRequestsPage.tsx
git commit -m "fix: align manager leave requests with backend"
```

- [ ] **Step 5: Push branch**

Run from repo root:

```powershell
git push origin phase9f/leave-requests-backend-integration
```

Expected: branch pushed and GitHub returns a PR URL.

## Self-Review Notes

- Spec coverage: covers manager list/review and employee list/create/cancel, mock/backend switch, cancelled status, builds, and API smoke.
- Placeholder scan: no unresolved placeholders remain.
- Type consistency: backend fields match `AdminLeaveRequestResponse`, `EmployeeLeaveRequestResponse`, `CreateLeaveRequestRequest`, and `ReviewLeaveRequestRequest`.
- Scope check: schedule generation, AI, notifications, and activity-log UI remain excluded.

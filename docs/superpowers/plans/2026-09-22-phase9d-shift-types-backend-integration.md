# Phase 9D Shift Types Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the existing manager shift type page to backend shift type APIs while preserving mock mode.

**Architecture:** Keep `ShiftTypesPage` consuming existing frontend `ShiftType` models. Add backend DTO types, an adapter, and a backend service; update `shiftTypeService` to switch between mock and backend by `apiConfig.useMockApi`.

**Tech Stack:** React, TypeScript, Vite, Spring Boot backend APIs already implemented.

---

## File Structure

- Create `frontend/src/types/backendShiftType.ts`: backend request/response contracts for `/api/admin/shift-types`.
- Create `frontend/src/adapters/shiftTypeAdapter.ts`: maps backend shift type DTOs to existing UI models and maps UI form values to backend requests.
- Create `frontend/src/services/backendShiftTypeService.ts`: authenticated calls to backend list/create/update/lock/unlock endpoints and client-side pagination.
- Modify `frontend/src/services/shiftTypeService.ts`: preserve current mock behavior, route backend mode to `backendShiftTypeService`.
- Modify `frontend/src/pages/manager/ShiftTypesPage.tsx`: update delete wording to backend-safe soft deactivation wording.

Backend endpoints already exist:

- `GET /api/admin/shift-types?query=&status=` returns `AdminShiftTypeResponse[]`.
- `POST /api/admin/shift-types` creates an active shift type.
- `PUT /api/admin/shift-types/{shiftTypeId}` updates a shift type.
- `POST /api/admin/shift-types/{shiftTypeId}/lock` sets status `INACTIVE`.
- `POST /api/admin/shift-types/{shiftTypeId}/unlock` sets status `ACTIVE`.

The backend has no hard-delete endpoint in this slice. Frontend `remove` should map to backend lock/deactivation.

### Task 1: Add Backend Shift Type Types And Adapter

**Files:**
- Create: `frontend/src/types/backendShiftType.ts`
- Create: `frontend/src/adapters/shiftTypeAdapter.ts`

- [ ] **Step 1: Add backend DTO types**

Create `frontend/src/types/backendShiftType.ts`:

```ts
import type { ShiftTypeStatus } from '@/types/management'

export type BackendQuotaCategory = 'REGULAR' | 'OVERTIME' | 'BATHING' | 'ON_CALL'

export interface BackendShiftTypeResponse {
  id: number
  shiftName: string
  startTime: string
  endTime: string
  durationHours: number | string
  overnight: boolean
  heavy: boolean
  minStaff: number
  maxStaff: number
  quotaCategory: BackendQuotaCategory
  status: ShiftTypeStatus
  shiftCode: string
  applicableWeekdays: string
  appliesOnHolidays: boolean
  penaltyWeightMultiplier: number | string
}

export interface BackendCreateShiftTypeRequest {
  shiftName: string
  startTime: string
  endTime: string
  durationHours: number
  overnight: boolean
  heavy: boolean
  minStaff: number
  maxStaff: number
  quotaCategory: BackendQuotaCategory
  shiftCode: string
  applicableWeekdays: string
  appliesOnHolidays: boolean
  penaltyWeightMultiplier: number
}

export interface BackendUpdateShiftTypeRequest extends BackendCreateShiftTypeRequest {
  status: ShiftTypeStatus
}
```

- [ ] **Step 2: Add adapter mapping helpers**

Create `frontend/src/adapters/shiftTypeAdapter.ts`:

```ts
import type {
  BackendCreateShiftTypeRequest,
  BackendQuotaCategory,
  BackendShiftTypeResponse,
  BackendUpdateShiftTypeRequest,
} from '@/types/backendShiftType'
import type { ShiftType, ShiftTypeFormValues } from '@/types/management'

const quotaToGroup = (quotaCategory: BackendQuotaCategory): ShiftType['group'] => {
  if (quotaCategory === 'BATHING') return 'THONG_TAM'
  if (quotaCategory === 'OVERTIME') return 'NGOAI_GIO'
  if (quotaCategory === 'ON_CALL') return 'CA_TRUC'
  return 'THONG_TAM'
}

const groupToQuota = (group: ShiftTypeFormValues['group']): BackendQuotaCategory => {
  if (group === 'NGOAI_GIO') return 'OVERTIME'
  if (group === 'CA_TRUC') return 'ON_CALL'
  return 'BATHING'
}

const weekdaysForDayType = (dayType: ShiftTypeFormValues['applicableDayType']) => (
  dayType === 'WEEKDAY' ? 'MON,TUE,WED,THU,FRI' : 'SAT,SUN'
)

const dayTypeFromBackend = (shiftType: BackendShiftTypeResponse): ShiftType['applicableDayType'] => (
  shiftType.appliesOnHolidays || shiftType.applicableWeekdays.includes('SAT') || shiftType.applicableWeekdays.includes('SUN')
    ? 'WEEKEND_OR_HOLIDAY'
    : 'WEEKDAY'
)

const durationHoursToMinutes = (durationHours: number | string) => Math.round(Number(durationHours) * 60)
const workMinutesToDurationHours = (workMinutes: number) => Number((workMinutes / 60).toFixed(1))

export const adaptBackendShiftType = (shiftType: BackendShiftTypeResponse): ShiftType => ({
  id: String(shiftType.id),
  code: shiftType.shiftCode,
  name: shiftType.shiftName,
  group: quotaToGroup(shiftType.quotaCategory),
  startTime: shiftType.startTime.slice(0, 5),
  endTime: shiftType.endTime.slice(0, 5),
  crossesMidnight: shiftType.overnight,
  breakStart: undefined,
  breakEnd: undefined,
  breakMinutes: 0,
  workMinutes: durationHoursToMinutes(shiftType.durationHours),
  applicableDayType: dayTypeFromBackend(shiftType),
  color: shiftType.heavy ? '#ef4444' : '#2563eb',
  description: `Min ${shiftType.minStaff}, max ${shiftType.maxStaff}`,
  status: shiftType.status,
})

export const toBackendCreateShiftTypeRequest = (form: ShiftTypeFormValues): BackendCreateShiftTypeRequest => ({
  shiftName: form.name.trim(),
  startTime: form.startTime,
  endTime: form.endTime,
  durationHours: workMinutesToDurationHours(form.workMinutes),
  overnight: form.crossesMidnight,
  heavy: form.group === 'THONG_TAM',
  minStaff: 1,
  maxStaff: 20,
  quotaCategory: groupToQuota(form.group),
  shiftCode: form.code.trim(),
  applicableWeekdays: weekdaysForDayType(form.applicableDayType),
  appliesOnHolidays: form.applicableDayType === 'WEEKEND_OR_HOLIDAY',
  penaltyWeightMultiplier: form.group === 'NGOAI_GIO' ? 1.2 : 1,
})

export const toBackendUpdateShiftTypeRequest = (form: ShiftTypeFormValues): BackendUpdateShiftTypeRequest => ({
  ...toBackendCreateShiftTypeRequest(form),
  status: form.status,
})
```

- [ ] **Step 3: Build to verify types compile**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/src/types/backendShiftType.ts frontend/src/adapters/shiftTypeAdapter.ts
git commit -m "feat: add backend shift type adapter"
```

### Task 2: Add Backend Shift Type Service

**Files:**
- Create: `frontend/src/services/backendShiftTypeService.ts`

- [ ] **Step 1: Add service implementation**

Create `frontend/src/services/backendShiftTypeService.ts`:

```ts
import { adaptBackendShiftType, toBackendCreateShiftTypeRequest, toBackendUpdateShiftTypeRequest } from '@/adapters/shiftTypeAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { BackendShiftTypeResponse } from '@/types/backendShiftType'
import type { PaginatedResult, ShiftType, ShiftTypeFormValues } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
}

const paginate = (items: ShiftType[], page: number, pageSize: number): PaginatedResult<ShiftType> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize

  return {
    data: items.slice(startIndex, startIndex + pageSize),
    page: safePage,
    pageSize,
    total,
    totalPages,
  }
}

const shiftTypePath = (id: string) => `/api/admin/shift-types/${encodeURIComponent(id)}`

export const backendShiftTypeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: ListParams = {}) {
    const params = new URLSearchParams()
    if (search.trim()) params.set('query', search.trim())
    if (status !== 'ALL') params.set('status', status)

    const response = await requestJson<BackendShiftTypeResponse[]>(`/api/admin/shift-types${params.size ? `?${params}` : ''}`)
    return paginate(response.map(adaptBackendShiftType), page, pageSize)
  },

  async getById(id: string) {
    const result = await this.list({ page: 1, pageSize: Number.MAX_SAFE_INTEGER })
    return result.data.find((shiftType) => shiftType.id === id) ?? null
  },

  async add(data: ShiftTypeFormValues) {
    const response = await requestJson<BackendShiftTypeResponse>('/api/admin/shift-types', {
      method: 'POST',
      body: toBackendCreateShiftTypeRequest(data),
    })
    return adaptBackendShiftType(response)
  },

  async update(id: string, data: ShiftTypeFormValues) {
    const response = await requestJson<BackendShiftTypeResponse>(shiftTypePath(id), {
      method: 'PUT',
      body: toBackendUpdateShiftTypeRequest(data),
    })
    return adaptBackendShiftType(response)
  },

  async toggleStatus(id: string) {
    const current = await this.getById(id)
    if (!current) {
      throw new Error('Loại ca trực không tồn tại.')
    }

    if (current.status === 'ACTIVE') {
      await requestVoid(`${shiftTypePath(id)}/lock`, { method: 'POST' })
    } else {
      await requestVoid(`${shiftTypePath(id)}/unlock`, { method: 'POST' })
    }

    const updated = await this.getById(id)
    if (!updated) {
      throw new Error('Không thể tải lại loại ca trực sau khi đổi trạng thái.')
    }
    return updated
  },

  async remove(id: string) {
    await requestVoid(`${shiftTypePath(id)}/lock`, { method: 'POST' })
    const updated = await this.getById(id)
    if (!updated) {
      throw new Error('Không thể tải lại loại ca trực sau khi tạm ngưng.')
    }
    return updated
  },
}
```

- [ ] **Step 2: Build to verify the new service compiles**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 3: Commit**

Run:

```powershell
git add frontend/src/services/backendShiftTypeService.ts
git commit -m "feat: add backend shift type service"
```

### Task 3: Route Public Service To Backend Mode

**Files:**
- Modify: `frontend/src/services/shiftTypeService.ts`

- [ ] **Step 1: Add imports**

At the top of `frontend/src/services/shiftTypeService.ts`, add:

```ts
import { apiConfig } from '@/config/api'
import { backendShiftTypeService } from '@/services/backendShiftTypeService'
```

- [ ] **Step 2: Route `list` to backend mode**

At the beginning of `list`, before `await delay(300)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.list({ page, pageSize, search, status })
    }
```

- [ ] **Step 3: Route `getById` to backend mode**

At the beginning of `getById`, before `await delay(200)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.getById(id)
    }
```

- [ ] **Step 4: Route `add` to backend mode**

At the beginning of `add`, before `await delay(350)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.add(data)
    }
```

- [ ] **Step 5: Route `update` to backend mode**

At the beginning of `update`, before `await delay(300)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.update(id, data)
    }
```

- [ ] **Step 6: Route `toggleStatus` to backend mode**

At the beginning of `toggleStatus`, before `await delay(250)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.toggleStatus(id)
    }
```

- [ ] **Step 7: Route `remove` to backend lock in backend mode**

At the beginning of `remove`, before `await delay(250)`, add:

```ts
    if (!apiConfig.useMockApi) {
      return backendShiftTypeService.remove(id)
    }
```

- [ ] **Step 8: Build in default mock mode**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 9: Build in backend mode**

Run from `frontend`:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: build passes.

- [ ] **Step 10: Commit**

Run:

```powershell
git add frontend/src/services/shiftTypeService.ts
git commit -m "feat: wire shift type service to backend"
```

### Task 4: Align Shift Type Page Copy With Backend Soft Deactivation

**Files:**
- Modify: `frontend/src/pages/manager/ShiftTypesPage.tsx`

- [ ] **Step 1: Change delete action labels to temporary deactivation**

In the action button at the table row, replace the aria-label:

```tsx
aria-label="Xóa ca"
```

with:

```tsx
aria-label="Tạm ngưng ca"
```

In the confirm dialog, replace:

```tsx
title="Xóa loại ca trực"
description={`Loại ca ${deleteTarget?.name} đang được dùng trong mock schedule. Bạn nên tạm ngưng thay vì xóa ngay.`}
```

with:

```tsx
title="Tạm ngưng loại ca trực"
description={`Loại ca ${deleteTarget?.name} sẽ được chuyển sang trạng thái tạm ngưng. Lịch sử ca đã sử dụng vẫn được giữ nguyên.`}
```

- [ ] **Step 2: Change fallback error copy**

In `handleDelete`, replace:

```ts
window.alert(deleteError instanceof Error ? deleteError.message : 'Không thể xóa loại ca.')
```

with:

```ts
window.alert(deleteError instanceof Error ? deleteError.message : 'Không thể tạm ngưng loại ca.')
```

- [ ] **Step 3: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/src/pages/manager/ShiftTypesPage.tsx
git commit -m "fix: clarify shift type deactivation copy"
```

### Task 5: Verify Backend Flow And Push Branch

**Files:**
- No required code changes.

- [ ] **Step 1: Confirm clean diff check**

Run from repo root:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 2: Run final frontend builds**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

Then run:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: build passes.

- [ ] **Step 3: Browser smoke**

With backend and frontend running in backend mode:

1. Login with manager account `tx-test-manager / 1111@`.
2. Open `/manager/shift-types`.
3. Confirm backend list loads.
4. Create a shift type using a unique code such as `TEST_9D_<timestamp>`.
5. Edit its name or time.
6. Toggle status inactive/active.
7. Use the deactivation action and confirm it becomes inactive.

- [ ] **Step 4: Commit any fixes discovered during smoke**

If browser smoke reveals a code fix, commit only the touched files with a concise message.

- [ ] **Step 5: Push branch**

Run from repo root:

```powershell
git push origin phase9d/shift-types-backend-integration
```

Expected: branch is pushed and GitHub returns a PR creation URL.

## Self-Review Notes

- Spec coverage: the plan covers backend DTO types, adapter, backend service, public service routing, UI copy alignment, builds, smoke test, and push.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: backend request/response fields match `AdminShiftTypeResponse`, `CreateShiftTypeRequest`, and `UpdateShiftTypeRequest`; frontend public model remains `ShiftType`.
- Scope check: Work Shift UI and schedule pages remain excluded.

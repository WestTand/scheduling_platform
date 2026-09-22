# Phase 9E Scheduling Rules Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a manager `Quy định xếp lịch` page connected to backend scheduling rule APIs.

**Architecture:** Add frontend scheduling-rule DTOs, adapters, and services using the same mock/backend switch pattern as employees and shift types. Add a focused manager page that consumes frontend UI models only, then wire it into routes and sidebar.

**Tech Stack:** React, TypeScript, Vite, Spring Boot backend APIs already implemented.

---

## File Structure

- Create `frontend/src/types/schedulingRule.ts`: frontend UI model and form-value types.
- Create `frontend/src/types/backendSchedulingRule.ts`: backend DTO shape for `GET/PUT /api/admin/scheduling-rules`.
- Create `frontend/src/adapters/schedulingRuleAdapter.ts`: maps backend DTOs to frontend model and frontend form values to backend request.
- Create `frontend/src/services/backendSchedulingRuleService.ts`: authenticated `GET` and `PUT` calls.
- Create `frontend/src/services/schedulingRuleService.ts`: public mock/backend-aware service.
- Create `frontend/src/pages/manager/SchedulingRulesPage.tsx`: manager form page.
- Modify `frontend/src/routes/AppRoutes.tsx`: import and route `/manager/scheduling-rules`.
- Modify `frontend/src/mocks/dashboardData.ts`: add sidebar item.
- Modify `frontend/src/components/layout/MainLayout.tsx`: add breadcrumb label.

### Task 1: Add Scheduling Rule Types And Adapter

**Files:**
- Create: `frontend/src/types/schedulingRule.ts`
- Create: `frontend/src/types/backendSchedulingRule.ts`
- Create: `frontend/src/adapters/schedulingRuleAdapter.ts`

- [ ] **Step 1: Create frontend scheduling rule types**

Create `frontend/src/types/schedulingRule.ts`:

```ts
export interface SchedulingHardRules {
  minRestHoursBetweenShifts: number
  maxWorkHoursPerDay: number
  maxShiftsPerWeek: number
}

export interface SchedulingQuotaRules {
  overtimeShiftsPerWeek: number
  bathingShiftsPerWeek: number
  onCallEveryNDays: number
}

export interface SchedulingSoftWeights {
  fairness: number
  preference: number
  weekend: number
  heavyShift: number
  workHourBalance: number
}

export interface SchedulingRules {
  hardRules: SchedulingHardRules
  quotas: SchedulingQuotaRules
  softWeights: SchedulingSoftWeights
}

export type SchedulingRulesFormValues = SchedulingRules
```

- [ ] **Step 2: Create backend DTO types**

Create `frontend/src/types/backendSchedulingRule.ts`:

```ts
export interface BackendSchedulingHardRules {
  minRestHoursBetweenShifts: number
  maxWorkHoursPerDay: number
  maxShiftsPerWeek: number
}

export interface BackendSchedulingQuotaRules {
  overtimeShiftsPerWeek: number
  bathingShiftsPerWeek: number
  onCallEveryNDays: number
}

export interface BackendSchedulingSoftWeights {
  fairness: number | string
  preference: number | string
  weekend: number | string
  heavyShift: number | string
  workHourBalance: number | string
}

export interface BackendSchedulingRulesResponse {
  hardRules: BackendSchedulingHardRules
  quotas: BackendSchedulingQuotaRules
  softWeights: BackendSchedulingSoftWeights
}

export type BackendUpdateSchedulingRulesRequest = BackendSchedulingRulesResponse
```

- [ ] **Step 3: Create adapter**

Create `frontend/src/adapters/schedulingRuleAdapter.ts`:

```ts
import type { BackendSchedulingRulesResponse, BackendUpdateSchedulingRulesRequest } from '@/types/backendSchedulingRule'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const decimal = (value: number | string) => Number(value)
const twoDecimals = (value: number) => Number(value.toFixed(2))

export const adaptBackendSchedulingRules = (rules: BackendSchedulingRulesResponse): SchedulingRules => ({
  hardRules: {
    minRestHoursBetweenShifts: rules.hardRules.minRestHoursBetweenShifts,
    maxWorkHoursPerDay: rules.hardRules.maxWorkHoursPerDay,
    maxShiftsPerWeek: rules.hardRules.maxShiftsPerWeek,
  },
  quotas: {
    overtimeShiftsPerWeek: rules.quotas.overtimeShiftsPerWeek,
    bathingShiftsPerWeek: rules.quotas.bathingShiftsPerWeek,
    onCallEveryNDays: rules.quotas.onCallEveryNDays,
  },
  softWeights: {
    fairness: decimal(rules.softWeights.fairness),
    preference: decimal(rules.softWeights.preference),
    weekend: decimal(rules.softWeights.weekend),
    heavyShift: decimal(rules.softWeights.heavyShift),
    workHourBalance: decimal(rules.softWeights.workHourBalance),
  },
})

export const toBackendSchedulingRulesRequest = (rules: SchedulingRulesFormValues): BackendUpdateSchedulingRulesRequest => ({
  hardRules: {
    minRestHoursBetweenShifts: rules.hardRules.minRestHoursBetweenShifts,
    maxWorkHoursPerDay: rules.hardRules.maxWorkHoursPerDay,
    maxShiftsPerWeek: rules.hardRules.maxShiftsPerWeek,
  },
  quotas: {
    overtimeShiftsPerWeek: rules.quotas.overtimeShiftsPerWeek,
    bathingShiftsPerWeek: rules.quotas.bathingShiftsPerWeek,
    onCallEveryNDays: rules.quotas.onCallEveryNDays,
  },
  softWeights: {
    fairness: twoDecimals(rules.softWeights.fairness),
    preference: twoDecimals(rules.softWeights.preference),
    weekend: twoDecimals(rules.softWeights.weekend),
    heavyShift: twoDecimals(rules.softWeights.heavyShift),
    workHourBalance: twoDecimals(rules.softWeights.workHourBalance),
  },
})
```

- [ ] **Step 4: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/types/schedulingRule.ts frontend/src/types/backendSchedulingRule.ts frontend/src/adapters/schedulingRuleAdapter.ts
git commit -m "feat: add scheduling rule adapters"
```

### Task 2: Add Scheduling Rule Services

**Files:**
- Create: `frontend/src/services/backendSchedulingRuleService.ts`
- Create: `frontend/src/services/schedulingRuleService.ts`

- [ ] **Step 1: Create backend service**

Create `frontend/src/services/backendSchedulingRuleService.ts`:

```ts
import { adaptBackendSchedulingRules, toBackendSchedulingRulesRequest } from '@/adapters/schedulingRuleAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendSchedulingRulesResponse } from '@/types/backendSchedulingRule'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const path = '/api/admin/scheduling-rules'

export const backendSchedulingRuleService = {
  async getRules(): Promise<SchedulingRules> {
    const response = await requestJson<BackendSchedulingRulesResponse>(path)
    return adaptBackendSchedulingRules(response)
  },

  async updateRules(values: SchedulingRulesFormValues): Promise<SchedulingRules> {
    const response = await requestJson<BackendSchedulingRulesResponse>(path, {
      method: 'PUT',
      body: toBackendSchedulingRulesRequest(values),
    })
    return adaptBackendSchedulingRules(response)
  },
}
```

- [ ] **Step 2: Create public mock/backend service**

Create `frontend/src/services/schedulingRuleService.ts`:

```ts
import { apiConfig } from '@/config/api'
import { backendSchedulingRuleService } from '@/services/backendSchedulingRuleService'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const defaultRules: SchedulingRules = {
  hardRules: {
    minRestHoursBetweenShifts: 8,
    maxWorkHoursPerDay: 12,
    maxShiftsPerWeek: 6,
  },
  quotas: {
    overtimeShiftsPerWeek: 2,
    bathingShiftsPerWeek: 2,
    onCallEveryNDays: 3,
  },
  softWeights: {
    fairness: 1,
    preference: 1,
    weekend: 1,
    heavyShift: 1,
    workHourBalance: 1,
  },
}

let mockRules = clone(defaultRules)

export const schedulingRuleService = {
  async getRules(): Promise<SchedulingRules> {
    if (!apiConfig.useMockApi) {
      return backendSchedulingRuleService.getRules()
    }
    await delay(250)
    return clone(mockRules)
  },

  async updateRules(values: SchedulingRulesFormValues): Promise<SchedulingRules> {
    if (!apiConfig.useMockApi) {
      return backendSchedulingRuleService.updateRules(values)
    }
    await delay(300)
    mockRules = clone(values)
    return clone(mockRules)
  },

  reset() {
    mockRules = clone(defaultRules)
  },
}
```

- [ ] **Step 3: Build backend mode and default mode**

Run from `frontend`:

```powershell
npm.cmd run build
```

Then:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: both builds pass.

- [ ] **Step 4: Commit**

```powershell
git add frontend/src/services/backendSchedulingRuleService.ts frontend/src/services/schedulingRuleService.ts
git commit -m "feat: add scheduling rule services"
```

### Task 3: Add Scheduling Rules Page

**Files:**
- Create: `frontend/src/pages/manager/SchedulingRulesPage.tsx`

- [ ] **Step 1: Create page component**

Create `frontend/src/pages/manager/SchedulingRulesPage.tsx`. The component must:

- Use `PageHeader`, `LoadingState`, `ErrorState`, `Card`, `CardContent`, and `Button`.
- Load rules on mount with `schedulingRuleService.getRules()`.
- Keep `formState`, `loadedRules`, `loading`, `error`, `formError`, `success`, `isSaving` state.
- Render three cards: `Quy định bắt buộc`, `Hạn mức ca theo tuần`, `Trọng số ưu tiên`.
- Save with `schedulingRuleService.updateRules(formState)`.
- Reset local form to `loadedRules` without API call.

Use this validation function exactly:

```ts
const validateRules = (rules: SchedulingRulesFormValues) => {
  if (rules.hardRules.minRestHoursBetweenShifts < 0 || rules.hardRules.minRestHoursBetweenShifts > 24) return 'Thời gian nghỉ tối thiểu phải từ 0 đến 24 giờ.'
  if (rules.hardRules.maxWorkHoursPerDay < 1 || rules.hardRules.maxWorkHoursPerDay > 24) return 'Số giờ làm tối đa mỗi ngày phải từ 1 đến 24.'
  if (rules.hardRules.maxShiftsPerWeek < 0 || rules.hardRules.maxShiftsPerWeek > 14) return 'Số ca tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.overtimeShiftsPerWeek < 0 || rules.quotas.overtimeShiftsPerWeek > 14) return 'Số ngày ngoài giờ tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.bathingShiftsPerWeek < 0 || rules.quotas.bathingShiftsPerWeek > 14) return 'Số ngày thông tầm tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.onCallEveryNDays < 1 || rules.quotas.onCallEveryNDays > 31) return 'Khoảng cách ngày trực phải từ 1 đến 31 ngày.'
  const weights = Object.values(rules.softWeights)
  if (weights.some((value) => value < 0 || value > 999.99)) return 'Trọng số ưu tiên phải từ 0 đến 999.99.'
  return ''
}
```

Use helper input updates like:

```ts
const updateHardRule = (key: keyof SchedulingRulesFormValues['hardRules'], value: number) => {
  setFormState((current) => current ? { ...current, hardRules: { ...current.hardRules, [key]: value } } : current)
}
```

Use Vietnamese labels exactly for quota fields:

```tsx
<NumberField label="Số ngày ngoài giờ tối đa / tuần" value={formState.quotas.overtimeShiftsPerWeek} onChange={(value) => updateQuota('overtimeShiftsPerWeek', value)} min={0} max={14} />
<NumberField label="Số ngày thông tầm tối đa / tuần" value={formState.quotas.bathingShiftsPerWeek} onChange={(value) => updateQuota('bathingShiftsPerWeek', value)} min={0} max={14} />
<NumberField label="Khoảng cách tối thiểu giữa ngày trực" value={formState.quotas.onCallEveryNDays} onChange={(value) => updateQuota('onCallEveryNDays', value)} min={1} max={31} />
```

- [ ] **Step 2: Build**

Run from `frontend`:

```powershell
npm.cmd run build
```

Expected: build passes.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/pages/manager/SchedulingRulesPage.tsx
git commit -m "feat: add scheduling rules page"
```

### Task 4: Wire Route And Sidebar

**Files:**
- Modify: `frontend/src/routes/AppRoutes.tsx`
- Modify: `frontend/src/mocks/dashboardData.ts`
- Modify: `frontend/src/components/layout/MainLayout.tsx`

- [ ] **Step 1: Add route import**

In `frontend/src/routes/AppRoutes.tsx`, add:

```ts
import { SchedulingRulesPage } from '@/pages/manager/SchedulingRulesPage'
```

- [ ] **Step 2: Add manager route**

Add this route after `/manager/shift-types`:

```tsx
        <Route
          path="/manager/scheduling-rules"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <SchedulingRulesPage />
            </ProtectedRoute>
          }
        />
```

- [ ] **Step 3: Add sidebar item**

In `frontend/src/mocks/dashboardData.ts`, insert after `Loại ca trực`:

```ts
    { label: 'Quy định xếp lịch', href: '/manager/scheduling-rules', icon: 'ClipboardList' },
```

- [ ] **Step 4: Add breadcrumb label**

In `frontend/src/components/layout/MainLayout.tsx`, add to `breadcrumbLabels`:

```ts
  'scheduling-rules': 'Quy định xếp lịch',
```

- [ ] **Step 5: Build both modes**

Run from `frontend`:

```powershell
npm.cmd run build
```

Then:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: both builds pass.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/routes/AppRoutes.tsx frontend/src/mocks/dashboardData.ts frontend/src/components/layout/MainLayout.tsx
git commit -m "feat: route scheduling rules page"
```

### Task 5: Verify Backend API And Push

**Files:**
- No required code changes.

- [ ] **Step 1: Verify backend API with fresh manager token**

Run from repo root:

```powershell
$body = @{ username = 'tx-test-manager'; password = '1111@' } | ConvertTo-Json; $login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body; $token = $login.accessToken; curl.exe -s -i "http://localhost:8080/api/admin/scheduling-rules" -H "Authorization: Bearer $token"
```

Expected: HTTP status `200` and JSON containing `hardRules`, `quotas`, and `softWeights`.

- [ ] **Step 2: Run final checks**

Run from repo root:

```powershell
git diff --check
```

Run from `frontend`:

```powershell
npm.cmd run build
```

Run from `frontend`:

```powershell
$env:VITE_USE_MOCK_API='false'; $env:VITE_API_BASE_URL='http://localhost:8080'; npm.cmd run build; Remove-Item Env:\VITE_USE_MOCK_API; Remove-Item Env:\VITE_API_BASE_URL
```

Expected: `git diff --check` has no output and both builds pass.

- [ ] **Step 3: Browser smoke**

With backend and frontend running in backend mode:

1. Login as `tx-test-manager / 1111@`.
2. Open `/manager/scheduling-rules` from sidebar.
3. Confirm rules load.
4. Change `fairness` by a small amount.
5. Save and confirm success message.
6. Reload the page and confirm the saved value remains.

- [ ] **Step 4: Push branch**

Run from repo root:

```powershell
git push origin phase9e/scheduling-rules-backend-integration
```

Expected: branch pushed and GitHub returns a PR URL.

## Self-Review Notes

- Spec coverage: covers DTOs, adapter, services, new page, route/sidebar, mock/backend switch, builds, API verification, and browser smoke.
- Placeholder scan: no unresolved placeholders remain.
- Type consistency: backend DTO fields match `AdminSchedulingRulesResponse`, `SchedulingHardRulesRequest`, `SchedulingQuotaRulesRequest`, and `SchedulingSoftWeightsRequest`.
- Scope check: schedule generation, work shifts, assignments, and AI pages are not changed.

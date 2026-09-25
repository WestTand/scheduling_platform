# Schedule Detail Backend Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish `/manager/schedules/:id` so it renders backend schedule detail data and backend status actions consistently.

**Architecture:** Keep behavior local to `ScheduleDetailPage` plus a small helper file for testable status-action rules. Use existing `backendScheduleService` methods for detail, approve, publish, and cancel. Reload detail after every successful status action.

**Tech Stack:** React 19, TypeScript, Vitest helper tests, existing backend schedule service.

---

## File Structure

- Create: `frontend/src/pages/manager/ScheduleDetailPage.helpers.ts` for action visibility and notice styling helpers.
- Create: `frontend/src/pages/manager/ScheduleDetailPage.test.ts` for helper tests.
- Modify: `frontend/src/pages/manager/ScheduleDetailPage.tsx` for backend loading, status actions, notices, and read-only messaging.

## Task 1: Detail Action Rules

**Files:**
- Create: `frontend/src/pages/manager/ScheduleDetailPage.helpers.ts`
- Create: `frontend/src/pages/manager/ScheduleDetailPage.test.ts`

- [ ] **Step 1: Write failing helper tests**

Create `frontend/src/pages/manager/ScheduleDetailPage.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage } from './ScheduleDetailPage.helpers'

describe('ScheduleDetailPage helpers', () => {
  it('shows backend status actions by schedule status', () => {
    expect(getScheduleDetailActions('DRAFT')).toEqual(['approve', 'cancel'])
    expect(getScheduleDetailActions('APPROVED')).toEqual(['publish', 'cancel'])
    expect(getScheduleDetailActions('PUBLISHED')).toEqual([])
    expect(getScheduleDetailActions('CANCELLED')).toEqual([])
  })

  it('shows read-only messages for immutable statuses', () => {
    expect(getScheduleDetailReadOnlyMessage('PUBLISHED')).toBe('Lịch đã công bố nên trang chi tiết chỉ cho phép xem.')
    expect(getScheduleDetailReadOnlyMessage('CANCELLED')).toBe('Lịch đã hủy nên trang chi tiết chỉ cho phép xem.')
    expect(getScheduleDetailReadOnlyMessage('DRAFT')).toBe('')
  })

  it('styles success and error notices distinctly', () => {
    expect(getScheduleDetailNoticeClass('success')).toContain('border-blue-200')
    expect(getScheduleDetailNoticeClass('error')).toContain('border-red-200')
  })
})
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleDetailPage.test.ts
```

Expected: FAIL because the helper file does not exist yet.

- [ ] **Step 3: Implement helper file**

Create `frontend/src/pages/manager/ScheduleDetailPage.helpers.ts`:

```ts
import type { ScheduleStatus } from '@/types/schedule'

export type ScheduleDetailAction = 'approve' | 'publish' | 'cancel'
export type ScheduleDetailNotice = { type: 'success' | 'error'; message: string }

export const getScheduleDetailActions = (status: ScheduleStatus): ScheduleDetailAction[] => {
  if (status === 'DRAFT') return ['approve', 'cancel']
  if (status === 'APPROVED') return ['publish', 'cancel']
  return []
}

export const getScheduleDetailReadOnlyMessage = (status: ScheduleStatus) => {
  if (status === 'PUBLISHED') return 'Lịch đã công bố nên trang chi tiết chỉ cho phép xem.'
  if (status === 'CANCELLED') return 'Lịch đã hủy nên trang chi tiết chỉ cho phép xem.'
  return ''
}

export const getScheduleDetailNoticeClass = (type: ScheduleDetailNotice['type']) => type === 'error'
  ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'
```

- [ ] **Step 4: Run test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleDetailPage.test.ts
```

Expected: PASS.

## Task 2: Backend Detail Page Wiring

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleDetailPage.tsx`

- [ ] **Step 1: Read current page and identify existing mock-only branches**

Read:

```powershell
frontend/src/pages/manager/ScheduleDetailPage.tsx
```

Expected: identify existing `scheduleDetailSeed` use, no backend loading or action reload behavior.

- [ ] **Step 2: Implement backend loading and notices**

Update `ScheduleDetailPage.tsx` to:

```ts
const [backendResult, setBackendResult] = useState<{ scheduleId: string; detail: ScheduleDetail | null; error: string } | null>(null)
const [notice, setNotice] = useState<ScheduleDetailNotice | null>(null)
const mockDetail = scheduleId === scheduleDetailSeed.id ? scheduleDetailSeed : null
const detail = apiConfig.useMockApi ? mockDetail : backendResult && backendResult.scheduleId === scheduleId ? backendResult.detail : null
```

Use `useEffect` in backend mode to call `backendScheduleService.getScheduleDetail(scheduleId)` and update `backendResult`.

- [ ] **Step 3: Implement backend status actions**

Add `runScheduleAction(action)` that calls:

```ts
if (action === 'approve') await backendScheduleService.approvePeriod(detail.id)
if (action === 'publish') await backendScheduleService.publishPeriod(detail.id)
if (action === 'cancel') await backendScheduleService.cancelPeriod(detail.id)
const refreshed = await backendScheduleService.getScheduleDetail(detail.id)
setBackendResult({ scheduleId: detail.id, detail: refreshed, error: '' })
setNotice({ type: 'success', message: 'Đã cập nhật trạng thái lịch.' })
```

On error, set a red notice with the backend error message.

- [ ] **Step 4: Render backend actions and read-only message**

Use `getScheduleDetailActions(detail.status)` to render:

- `Duyệt` for `approve`.
- `Công bố` for `publish`.
- `Hủy` for `cancel`.

Always keep `Chỉnh sửa` as navigation, but if status is `PUBLISHED` or `CANCELLED`, also show `getScheduleDetailReadOnlyMessage(detail.status)`.

## Task 3: Verification

**Files:**
- No code changes unless verification reveals a defect.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleDetailPage.test.ts src/services/backendScheduleService.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run build**

Run:

```powershell
npm.cmd run build
```

Expected: PASS. Existing chunk-size warning is acceptable.

- [ ] **Step 3: Run lint**

Run:

```powershell
npm.cmd run lint
```

Expected: no lint errors. Existing `AuthContext.tsx` Fast Refresh warning may remain.

- [ ] **Step 4: Runtime smoke**

Open `/manager/schedules/1` in backend mode and confirm detail data loads. Since period 1 is currently `PUBLISHED`, confirm no approve/publish/cancel buttons render and the read-only message is visible.

## Self-Review

- Spec coverage: backend detail loading, counts/assignments display, status actions, reload, notices, and read-only messaging are covered.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: helper functions use existing `ScheduleStatus` and existing service methods.

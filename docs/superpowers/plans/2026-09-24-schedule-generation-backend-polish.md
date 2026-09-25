# Schedule Generation Backend Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish `/manager/schedule-generation` so backend mode can create a schedule period, optimize it, and link to the generated schedule detail.

**Architecture:** Keep page state in `ScheduleGenerationPage`, with small pure helpers in a companion helper file for testable UI rules. Reuse `backendScheduleService.createPeriod`, `listPeriods`, and `optimize`; no backend API changes. The form creates/selects a period, while `AI Optimize` runs only when a period is selected.

**Tech Stack:** React 19, TypeScript, Vitest helper tests, existing backend schedule service.

---

## File Structure

- Create: `frontend/src/pages/manager/ScheduleGenerationPage.helpers.ts` for optimize enablement, notices, and optimize summary formatting.
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.test.tsx` to test helper behavior.
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx` to create backend periods from the form and show optimize results.
- Modify: `frontend/src/services/backendScheduleService.test.ts` only if existing create/optimize coverage needs request assertions adjusted.

## Task 1: Helper Rules

**Files:**
- Create: `frontend/src/pages/manager/ScheduleGenerationPage.helpers.ts`
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.test.tsx`

- [ ] **Step 1: Write failing helper tests**

Append to `frontend/src/pages/manager/ScheduleGenerationPage.test.tsx`:

```tsx
import { canOptimizeSchedulePeriod, getScheduleGenerationNoticeClass, summarizeOptimizeResult } from './ScheduleGenerationPage.helpers'

describe('ScheduleGenerationPage helpers', () => {
  it('enables optimize only when a backend period is selected and not submitting', () => {
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '7', isSubmitting: false })).toBe(true)
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '', isSubmitting: false })).toBe(false)
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '7', isSubmitting: true })).toBe(false)
  })

  it('styles success and error notices distinctly', () => {
    expect(getScheduleGenerationNoticeClass('success')).toContain('border-blue-200')
    expect(getScheduleGenerationNoticeClass('error')).toContain('border-red-200')
  })

  it('formats optimize output with status and assignment count', () => {
    expect(summarizeOptimizeResult({ aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', assignmentCount: 6 })).toBe('AI: SUCCESS · Solver: OPTIMAL · 6 phân công')
    expect(summarizeOptimizeResult({ aiStatus: 'FAILED', solverStatus: null, assignmentCount: null })).toBe('AI: FAILED')
  })
})
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleGenerationPage.test.tsx
```

Expected: FAIL because helper file/functions do not exist.

- [ ] **Step 3: Implement helper file**

Create `frontend/src/pages/manager/ScheduleGenerationPage.helpers.ts`:

```ts
export type ScheduleGenerationNoticeType = 'success' | 'error'

export const canOptimizeSchedulePeriod = ({ selectedPeriodId, isSubmitting }: { selectedPeriodId: string; isSubmitting: boolean }) => Boolean(selectedPeriodId) && !isSubmitting

export const getScheduleGenerationNoticeClass = (type: ScheduleGenerationNoticeType) => type === 'error'
  ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'

export const summarizeOptimizeResult = ({ aiStatus, solverStatus, assignmentCount }: { aiStatus: string; solverStatus: string | null; assignmentCount: number | null }) => [
  `AI: ${aiStatus}`,
  solverStatus ? `Solver: ${solverStatus}` : '',
  typeof assignmentCount === 'number' ? `${assignmentCount} phân công` : '',
].filter(Boolean).join(' · ')
```

- [ ] **Step 4: Run test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleGenerationPage.test.tsx
```

Expected: PASS.

## Task 2: Create Period from Form

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx`

- [ ] **Step 1: Inspect form value shape**

Read `frontend/src/components/schedule/ScheduleGenerationForm.tsx` and confirm field names for period creation.

- [ ] **Step 2: Replace backend confirm behavior**

Change backend `handleConfirm` so it creates a period from `pendingValues` instead of optimizing immediately:

```ts
const created = await backendScheduleService.createPeriod({
  periodName: pendingValues.name,
  startDate: pendingValues.startDate,
  endDate: pendingValues.endDate,
  versionNo: 1,
})
setSelectedPeriodId(created.id)
setPeriodResult((current) => ({ periods: [created, ...(current?.periods.filter((period) => period.id !== created.id) ?? [])], error: '' }))
setNotice({ type: 'success', message: `Đã tạo kỳ lịch ${created.name}. Có thể chạy AI Optimize.` })
setPendingValues(null)
```

Keep mock mode unchanged.

- [ ] **Step 3: Convert notice state to typed notice**

Replace string `notice` and separate `error` render with:

```ts
const [notice, setNotice] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
```

Render with `getScheduleGenerationNoticeClass(notice.type)`.

## Task 3: Optimize Created or Selected Period

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx`

- [ ] **Step 1: Update optimize function**

Update `optimizeSelectedPeriod` to:

- require `selectedPeriodId`
- call `backendScheduleService.optimize(selectedPeriodId)`
- show success notice using `summarizeOptimizeResult(result)`
- keep user on the page
- show a `Xem chi tiết lịch` link/button to `/manager/schedules/{selectedPeriodId}`

- [ ] **Step 2: Update AI Optimize button**

Use:

```tsx
disabled={!canOptimizeSchedulePeriod({ selectedPeriodId, isSubmitting })}
```

Set button label to `Đang tối ưu...` while submitting.

- [ ] **Step 3: Add detail navigation link after period exists**

If `selectedPeriodId` is truthy, render:

```tsx
<Button variant="outline" onClick={() => navigate(`/manager/schedules/${selectedPeriodId}`)}>Xem chi tiết lịch</Button>
```

## Task 4: Verification

**Files:**
- No code changes unless verification reveals a defect.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleGenerationPage.test.tsx src/services/backendScheduleService.test.ts
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

In backend mode, open `/manager/schedule-generation`, submit a small valid period, confirm it appears as selected, then run `AI Optimize`. If the new period lacks work shifts and backend returns a validation error, verify the red error notice renders with backend message.

## Self-Review

- Spec coverage: create period, optimize enablement, optimize output, detail navigation, success/error notices, and no auto-navigation are covered.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: uses existing `ScheduleGenerationValues`, `WorkSchedule`, and backend service methods.

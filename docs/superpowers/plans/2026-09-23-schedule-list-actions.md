# Schedule List Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder schedule-list `Mở lịch` action with real navigation and schedule status actions.

**Architecture:** Keep the existing list/page split. `ScheduleList` stays presentational and receives callbacks; `SchedulesPage` owns backend calls, reloads data, and renders notices. `backendScheduleService` wraps existing approve/publish/cancel endpoints and returns adapted `WorkSchedule` objects.

**Tech Stack:** React 19, TypeScript, Vitest, Spring Boot backend API, existing `requestJson` API client.

---

## File Structure

- Modify `frontend/src/services/backendScheduleService.ts`: add `approvePeriod`, `publishPeriod`, `cancelPeriod` methods.
- Modify `frontend/src/services/backendScheduleService.test.ts`: verify the three endpoint calls.
- Modify `frontend/src/components/schedule/ScheduleList.tsx`: replace `onPlaceholderAction` with real action props and render status-aware buttons.
- Add or modify `frontend/src/components/schedule/ScheduleList.test.tsx`: static render test for real actions and absence of `Mở lịch` placeholder.
- Modify `frontend/src/pages/manager/SchedulesPage.tsx`: implement action handlers, backend reload, success/error notices.

## Task 1: Backend Schedule Service Status Actions

**Files:**
- Modify: `frontend/src/services/backendScheduleService.test.ts`
- Modify: `frontend/src/services/backendScheduleService.ts`

- [ ] **Step 1: Add failing service test for approve/publish/cancel**

Append this test inside `describe('backendScheduleService', () => { ... })` in `frontend/src/services/backendScheduleService.test.ts`:

```ts
  test('approves publishes and cancels schedule periods', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'APPROVED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: 1, approvedByUsername: 'manager@example.com', publishedAt: null, totalPenalty: null }))
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'PUBLISHED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: 1, approvedByUsername: 'manager@example.com', publishedAt: '2026-09-23T08:00:00', totalPenalty: null }))
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'CANCELLED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: null, approvedByUsername: null, publishedAt: null, totalPenalty: null }))

    const approved = await backendScheduleService.approvePeriod('7')
    const published = await backendScheduleService.publishPeriod('7')
    const cancelled = await backendScheduleService.cancelPeriod('7')

    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/admin/schedule-periods/7/approve', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/admin/schedule-periods/7/publish', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/admin/schedule-periods/7/cancel', expect.objectContaining({ method: 'PATCH' }))
    expect(approved.status).toBe('APPROVED')
    expect(published.status).toBe('PUBLISHED')
    expect(cancelled.status).toBe('CANCELLED')
  })
```

- [ ] **Step 2: Run service test and verify RED**

Run:

```powershell
npm.cmd test -- src/services/backendScheduleService.test.ts
```

Expected: FAIL because `approvePeriod`, `publishPeriod`, and `cancelPeriod` are not functions.

- [ ] **Step 3: Implement service methods**

Add these methods inside `backendScheduleService` in `frontend/src/services/backendScheduleService.ts`, after `createPeriod`:

```ts
  async approvePeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/approve`, { method: 'PATCH' }))
  },

  async publishPeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/publish`, { method: 'PATCH' }))
  },

  async cancelPeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/cancel`, { method: 'PATCH' }))
  },
```

- [ ] **Step 4: Run service test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/services/backendScheduleService.test.ts
```

Expected: PASS.

## Task 2: Schedule List Real Actions

**Files:**
- Create: `frontend/src/components/schedule/ScheduleList.test.tsx`
- Modify: `frontend/src/components/schedule/ScheduleList.tsx`

- [ ] **Step 1: Add failing static render test**

Create `frontend/src/components/schedule/ScheduleList.test.tsx`:

```tsx
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { ScheduleList } from '@/components/schedule/ScheduleList'
import { ScheduleStatus, type WorkSchedule } from '@/types/schedule'

const schedule = (status: WorkSchedule['status']): WorkSchedule => ({
  id: `schedule-${status}`,
  name: `Lịch ${status}`,
  startDate: '2026-09-21',
  endDate: '2026-09-27',
  createdBy: 'manager@example.com',
  createdAt: '2026-09-21T00:00:00',
  employeeCount: 4,
  assignmentCount: 6,
  shiftCount: 3,
  status,
  isMock: false,
})

describe('ScheduleList', () => {
  it('renders real actions instead of the Mo lich placeholder', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ScheduleList
          schedules={[schedule(ScheduleStatus.DRAFT), schedule(ScheduleStatus.APPROVED)]}
          onApprove={() => undefined}
          onPublish={() => undefined}
          onCancel={() => undefined}
        />
      </MemoryRouter>,
    )

    expect(html).toContain('Xem chi tiết')
    expect(html).toContain('Chỉnh sửa')
    expect(html).toContain('Duyệt')
    expect(html).toContain('Công bố')
    expect(html).toContain('Hủy')
    expect(html).not.toContain('Mở lịch')
  })
})
```

- [ ] **Step 2: Run component test and verify RED**

Run:

```powershell
npm.cmd test -- src/components/schedule/ScheduleList.test.tsx
```

Expected: FAIL because `ScheduleList` still expects `onPlaceholderAction` and renders `Mở lịch`.

- [ ] **Step 3: Update `ScheduleList` props and rendering**

In `frontend/src/components/schedule/ScheduleList.tsx`, replace imports and props with:

```tsx
import { CalendarDays, Eye, Pencil, Send, ShieldCheck, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { scheduleStatusLabels } from '@/mocks/schedules'
import { ScheduleStatus, type WorkSchedule } from '@/types/schedule'

interface ScheduleListProps {
  schedules: WorkSchedule[]
  onApprove: (schedule: WorkSchedule) => void
  onPublish: (schedule: WorkSchedule) => void
  onCancel: (schedule: WorkSchedule) => void
}
```

Then replace the action area lines 37-40 with:

```tsx
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={schedule.status === 'PUBLISHED' ? 'default' : schedule.status === 'DRAFT' ? 'secondary' : 'outline'}>{scheduleStatusLabels[schedule.status]}</Badge>
              <Button variant="outline" size="sm" asChild><Link to={`/manager/schedules/${schedule.id}`}><Eye className="mr-2 h-4 w-4" />Xem chi tiết</Link></Button>
              <Button variant="outline" size="sm" asChild><Link to={`/manager/schedules/${schedule.id}/edit`}><Pencil className="mr-2 h-4 w-4" />Chỉnh sửa</Link></Button>
              {schedule.status === ScheduleStatus.DRAFT && <Button variant="outline" size="sm" onClick={() => onApprove(schedule)}><ShieldCheck className="mr-2 h-4 w-4" />Duyệt</Button>}
              {schedule.status === ScheduleStatus.APPROVED && <Button variant="outline" size="sm" onClick={() => onPublish(schedule)}><Send className="mr-2 h-4 w-4" />Công bố</Button>}
              {(schedule.status === ScheduleStatus.DRAFT || schedule.status === ScheduleStatus.APPROVED) && <Button variant="outline" size="sm" className="text-red-600" onClick={() => onCancel(schedule)}><XCircle className="mr-2 h-4 w-4" />Hủy</Button>}
            </div>
```

- [ ] **Step 4: Run component test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/components/schedule/ScheduleList.test.tsx
```

Expected: PASS.

## Task 3: Wire Actions in Schedules Page

**Files:**
- Modify: `frontend/src/pages/manager/SchedulesPage.tsx`
- Modify: `frontend/src/components/schedule/ScheduleList.test.tsx` if prop names need alignment.

- [ ] **Step 1: Add action handler helper in `SchedulesPage`**

Inside `SchedulesPage`, after `updateFilters`, add:

```ts
  const reloadSchedules = async () => {
    if (apiConfig.useMockApi) return
    const items = await backendScheduleService.listPeriods({ search, status, startDate, endDate })
    setBackendResult({ key: requestKey, schedules: items, error: '' })
  }

  const runScheduleAction = async (schedule: WorkSchedule, action: 'approve' | 'publish' | 'cancel') => {
    if (apiConfig.useMockApi) {
      setNotice(`${action === 'approve' ? 'Duyệt' : action === 'publish' ? 'Công bố' : 'Hủy'} lịch sẽ được triển khai trong backend mode.`)
      return
    }
    try {
      if (action === 'approve') await backendScheduleService.approvePeriod(schedule.id)
      if (action === 'publish') await backendScheduleService.publishPeriod(schedule.id)
      if (action === 'cancel') await backendScheduleService.cancelPeriod(schedule.id)
      await reloadSchedules()
      setNotice(`${action === 'approve' ? 'Đã duyệt' : action === 'publish' ? 'Đã công bố' : 'Đã hủy'} lịch ${schedule.name}.`)
    } catch (actionError) {
      setBackendResult({ key: requestKey, schedules, error: actionError instanceof Error ? actionError.message : 'Không thể cập nhật trạng thái lịch.' })
    }
  }
```

- [ ] **Step 2: Replace `ScheduleList` usage**

Replace this line:

```tsx
      <ScheduleList schedules={schedules} onPlaceholderAction={(label) => setNotice(`${label} sẽ được triển khai trong Phase 4.`)} />
```

with:

```tsx
      <ScheduleList
        schedules={schedules}
        onApprove={(schedule) => void runScheduleAction(schedule, 'approve')}
        onPublish={(schedule) => void runScheduleAction(schedule, 'publish')}
        onCancel={(schedule) => void runScheduleAction(schedule, 'cancel')}
      />
```

- [ ] **Step 3: Run focused frontend tests**

Run:

```powershell
npm.cmd test -- src/services/backendScheduleService.test.ts src/components/schedule/ScheduleList.test.tsx
```

Expected: PASS.

## Task 4: Verification

**Files:**
- No new code files.

- [ ] **Step 1: Run full frontend tests**

Run:

```powershell
npm.cmd test
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Expected: TypeScript and Vite build pass. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 3: Run frontend lint**

Run:

```powershell
npm.cmd run lint
```

Expected: no lint errors. Existing `AuthContext.tsx` Fast Refresh warning may remain.

- [ ] **Step 4: Manual backend-mode smoke test**

With backend mode running at `http://localhost:5173`:

1. Login as `manager@example.com / Manager@123`.
2. Open `/manager/schedules`.
3. Confirm no `Mở lịch` placeholder notice is shown from list actions.
4. Click `Duyệt` on a draft schedule.
5. Confirm the schedule reloads as `Đã duyệt`.
6. Click `Công bố` on the approved schedule.
7. Confirm the schedule reloads as `Đã công bố`.

Expected: UI status changes match backend responses.

## Self-Review

- Spec coverage: all five actions are represented. Backend state actions call existing endpoints and reload the list.
- Placeholder scan: the plan contains no implementation TBD/TODO placeholders.
- Type consistency: `WorkSchedule`, `ScheduleStatus`, `backendScheduleService`, and handler signatures match existing frontend code.

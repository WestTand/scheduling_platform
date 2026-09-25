# Dashboard Backend Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the manager and employee dashboards to real backend data and remove mock content, dead links, and hardcoded dates.

**Architecture:** Dashboard-owned aggregation in the frontend. Each dashboard page fetches from existing services in parallel, shares one week-detail load, and derives view models through pure, unit-tested helpers. No backend changes. Mock rendering stays only when `VITE_USE_MOCK_API=true`.

**Tech Stack:** React 19, TypeScript, Vitest, existing frontend services (`backendEmployeeService`, `backendScheduleService`, `navigationSummaryService`, `leaveRequestService`, `swapRequestService`, `preferenceService`, `notificationService`, `employeeScheduleService`).

---

## File Structure

- Create `frontend/src/pages/manager/ManagerDashboard.helpers.ts`: pure view-model builders for summary cards, upcoming shifts, alerts, and action items.
- Create `frontend/src/pages/manager/ManagerDashboard.test.tsx`: unit tests for those helpers with backend-shaped fixtures.
- Modify `frontend/src/pages/manager/ManagerDashboardPage.tsx`: fetch real data in backend mode, compose via helpers, delete the pending-requests card and all mock imports/data, fix stale links and dates.
- Create `frontend/src/pages/employee/EmployeeDashboard.helpers.ts`: pure builders for employee summary cards and next-shift rows.
- Create `frontend/src/pages/employee/EmployeeDashboard.test.tsx`: unit tests for those helpers.
- Modify `frontend/src/pages/employee/EmployeeDashboardPage.tsx`: fetch real schedule, summary, and notifications in backend mode, delete mock data, fix stale links and dates.
- Reuse (no changes): `getEmployeeWeekWindow` and `adaptBackendEmployeeSchedule` from `frontend/src/pages/employee/EmployeeSchedulePage.helpers.ts`.

---

### Task 1: Manager summary and action-item helpers

**Files:**
- Create: `frontend/src/pages/manager/ManagerDashboard.helpers.ts`
- Test: `frontend/src/pages/manager/ManagerDashboard.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/manager/ManagerDashboard.test.tsx` with this exact content:

```tsx
import { describe, expect, test } from 'vitest'

import { getManagerActionItems, getManagerSummaryCards } from './ManagerDashboard.helpers'

describe('ManagerDashboard helpers', () => {
  test('builds summary cards from real counts', () => {
    expect(getManagerSummaryCards({
      employeeTotal: 8,
      weekShiftCount: 4,
      pendingLeave: 1,
      pendingSwap: 2,
      pendingPreference: 0,
      warningCount: 3,
    })).toEqual([
      { label: 'Tổng nhân viên', value: '8', context: 'đang hoạt động', path: '/manager/employees' },
      { label: 'Ca trực tuần này', value: '4', context: 'tuần hiện tại', path: '/manager/schedules' },
      { label: 'Yêu cầu chờ duyệt', value: '3', context: 'nghỉ phép, đổi ca, nguyện vọng', path: '/manager/dashboard#action-center' },
      { label: 'Cảnh báo xung đột', value: '3', context: 'kỳ nháp tuần này', path: '/manager/schedules' },
    ])
  })

  test('builds action items with empty fallbacks', () => {
    const items = getManagerActionItems({
      draftPeriod: null,
      leave: null,
      swap: null,
      preference: null,
      criticalWarning: null,
    })
    expect(items).toHaveLength(5)
    expect(items[0]).toMatchObject({ title: 'Lịch nháp chờ xử lý', related: 'Không có lịch nháp', path: '/manager/schedules?status=DRAFT' })
    expect(items[4]).toMatchObject({ title: 'Cảnh báo lịch nghiêm trọng', related: 'Không có cảnh báo' })
  })

  test('maps real queue heads into action items', () => {
    const items = getManagerActionItems({
      draftPeriod: { id: '48', name: 'Test002' },
      leave: { employeeName: 'Nguyễn Văn An', startDate: '2026-09-24', endDate: '2026-09-25' },
      swap: { employeeName: 'Trần Thị Bình', reason: 'Cover me' },
      preference: { employeeName: 'Lê Minh Châu', note: 'Muốn nghỉ thứ 7' },
      criticalWarning: { periodId: '48', employeeName: 'Nguyễn Văn An', content: 'Ca Truc sang thiếu người' },
    })
    expect(items[1]).toMatchObject({ related: 'Nguyễn Văn An', path: '/manager/leave-requests?status=PENDING' })
    expect(items[2]).toMatchObject({ related: 'Trần Thị Bình', path: '/manager/swap-requests?status=PENDING_APPROVAL' })
    expect(items[3]).toMatchObject({ related: 'Lê Minh Châu', path: '/manager/preferences?status=PENDING' })
    expect(items[4]).toMatchObject({ path: '/manager/schedules/48?tab=warnings' })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- ManagerDashboard.test.tsx`

Workdir: `frontend`

Expected: FAIL with "Cannot find module './ManagerDashboard.helpers'".

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/pages/manager/ManagerDashboard.helpers.ts` with this exact content:

```ts
export interface ManagerSummaryCard {
  label: string
  value: string
  context: string
  path: string
}

export interface ManagerActionItem {
  title: string
  related: string
  detail: string
  priority: 'Cao' | 'Trung bình'
  status: string
  path: string
}

export const getManagerSummaryCards = ({
  employeeTotal,
  weekShiftCount,
  pendingLeave,
  pendingSwap,
  pendingPreference,
  warningCount,
}: {
  employeeTotal: number
  weekShiftCount: number
  pendingLeave: number
  pendingSwap: number
  pendingPreference: number
  warningCount: number
}): ManagerSummaryCard[] => [
  { label: 'Tổng nhân viên', value: String(employeeTotal), context: 'đang hoạt động', path: '/manager/employees' },
  { label: 'Ca trực tuần này', value: String(weekShiftCount), context: 'tuần hiện tại', path: '/manager/schedules' },
  { label: 'Yêu cầu chờ duyệt', value: String(pendingLeave + pendingSwap + pendingPreference), context: 'nghỉ phép, đổi ca, nguyện vọng', path: '/manager/dashboard#action-center' },
  { label: 'Cảnh báo xung đột', value: String(warningCount), context: 'kỳ nháp tuần này', path: '/manager/schedules' },
]

export const getManagerActionItems = ({
  draftPeriod,
  leave,
  swap,
  preference,
  criticalWarning,
}: {
  draftPeriod: { id: string; name: string } | null
  leave: { employeeName: string; startDate: string; endDate: string } | null
  swap: { employeeName: string; reason: string } | null
  preference: { employeeName: string; note: string } | null
  criticalWarning: { periodId: string; employeeName: string; content: string } | null
}): ManagerActionItem[] => [
  {
    title: 'Lịch nháp chờ xử lý',
    related: draftPeriod?.name ?? 'Không có lịch nháp',
    detail: draftPeriod ? `Kỳ lịch ${draftPeriod.name}.` : '',
    priority: 'Cao',
    status: 'Chờ xử lý',
    path: '/manager/schedules?status=DRAFT',
  },
  {
    title: 'Ngày nghỉ chờ duyệt',
    related: leave?.employeeName ?? 'Không có yêu cầu',
    detail: leave ? `Yêu cầu từ ${leave.startDate} đến ${leave.endDate}.` : '',
    priority: 'Cao',
    status: 'Chờ duyệt',
    path: '/manager/leave-requests?status=PENDING',
  },
  {
    title: 'Đổi ca chờ xử lý',
    related: swap?.employeeName ?? 'Không có yêu cầu',
    detail: swap?.reason ?? '',
    priority: 'Trung bình',
    status: 'Chờ xử lý',
    path: '/manager/swap-requests?status=PENDING_APPROVAL',
  },
  {
    title: 'Nguyện vọng chưa xem',
    related: preference?.employeeName ?? 'Không có nguyện vọng',
    detail: preference?.note ?? '',
    priority: 'Trung bình',
    status: 'Chưa xem',
    path: '/manager/preferences?status=PENDING',
  },
  {
    title: 'Cảnh báo lịch nghiêm trọng',
    related: criticalWarning?.employeeName ?? 'Không có cảnh báo',
    detail: criticalWarning?.content ?? '',
    priority: 'Cao',
    status: 'Cần xem',
    path: criticalWarning ? `/manager/schedules/${criticalWarning.periodId}?tab=warnings` : '/manager/schedules',
  },
]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- ManagerDashboard.test.tsx`

Workdir: `frontend`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/manager/ManagerDashboard.helpers.ts frontend/src/pages/manager/ManagerDashboard.test.tsx
git commit -m "feat: add manager dashboard summary and action-item helpers"
```

---

### Task 2: Manager upcoming-shift and alert helpers

**Files:**
- Modify: `frontend/src/pages/manager/ManagerDashboard.helpers.ts`
- Modify: `frontend/src/pages/manager/ManagerDashboard.test.tsx`

- [ ] **Step 1: Write the failing test**

Append this block to `frontend/src/pages/manager/ManagerDashboard.test.tsx`:

```tsx
import { pickScheduleAlerts, pickUpcomingShifts } from './ManagerDashboard.helpers'

describe('ManagerDashboard shift helpers', () => {
  const detail = {
    id: '48',
    name: 'Test002',
    startDate: '2026-09-24',
    endDate: '2026-09-26',
    createdBy: 'Backend',
    createdAt: '2026-09-25T05:00:00',
    employeeCount: 8,
    assignmentCount: 1,
    shiftCount: 2,
    status: 'DRAFT',
    isMock: false,
    employeePool: ['Nguyễn Văn An'],
    assignments: [
      { id: '21', scheduleId: '48', workShiftId: '113', employeeId: '1', date: '2026-09-24', shiftCode: 'THONG_TAM', employeeNames: ['Nguyễn Văn An'], warningCount: 0, locked: false },
      { id: 'work-shift-114', scheduleId: '48', workShiftId: '114', date: '2026-09-24', shiftCode: 'NGOAI_GIO_1', employeeNames: [], warningCount: 1, locked: false },
    ],
    warnings: [
      { id: 'understaffed-shift-114', type: 'HARD_CONSTRAINT', severity: 'HIGH', employeeName: 'Pool: Nguyễn Văn An', date: '2026-09-24', shiftCode: 'NGOAI_GIO_1', content: 'Ca Ngoai gio 1 thiếu người', suggestion: 'Phân công thêm' },
    ],
  } as const

  test('picks upcoming shifts sorted by date', () => {
    const rows = pickUpcomingShifts([detail as never], 3)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({ periodId: '48', shiftCode: 'THONG_TAM', assignedCount: 1, unassigned: false, hasWarning: false })
    expect(rows[1]).toMatchObject({ shiftCode: 'NGOAI_GIO_1', assignedCount: 0, unassigned: true, hasWarning: true })
  })

  test('picks high-severity alerts first', () => {
    const alerts = pickScheduleAlerts([detail as never], 3)
    expect(alerts).toHaveLength(1)
    expect(alerts[0]).toMatchObject({ periodId: '48', severity: 'Cao', path: '/manager/schedules/48?tab=warnings' })
  })

  test('returns empty arrays for empty details', () => {
    expect(pickUpcomingShifts([], 3)).toEqual([])
    expect(pickScheduleAlerts([], 3)).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- ManagerDashboard.test.tsx`

Workdir: `frontend`

Expected: FAIL with "pickUpcomingShifts is not a function" (or "does not provide an export").

- [ ] **Step 3: Write minimal implementation**

Append this code to `frontend/src/pages/manager/ManagerDashboard.helpers.ts`:

```ts
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import type { ScheduleDetail } from '@/types/schedule'

export interface UpcomingShiftRow {
  key: string
  periodId: string
  date: string
  dateLabel: string
  shiftCode: string
  shiftName: string
  assignedCount: number
  unassigned: boolean
  hasWarning: boolean
  path: string
}

export interface ScheduleAlertRow {
  id: string
  periodId: string
  title: string
  detail: string
  severity: 'Cao' | 'Trung bình' | 'Thấp'
  path: string
}

const formatDateLabel = (date: string) => {
  const weekday = new Date(`${date}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'long' })
  const [year, month, day] = date.split('-')
  return `${weekday} - ${day}/${month}`
}

const alertSeverityLabel = (severity: string): ScheduleAlertRow['severity'] => {
  if (severity === 'HIGH') return 'Cao'
  if (severity === 'MEDIUM') return 'Trung bình'
  return 'Thấp'
}

const alertTitle = (type: string) => {
  if (type === 'HARD_CONSTRAINT') return 'Vi phạm ràng buộc cứng'
  if (type === 'SOFT_CONSTRAINT') return 'Cảnh báo mềm'
  return 'Thông tin lịch'
}

export const pickUpcomingShifts = (details: ScheduleDetail[], limit: number): UpcomingShiftRow[] => {
  const rows = details.flatMap((detail) => detail.assignments.map((assignment) => {
    const assignedCount = assignment.employeeNames.length
    const unassigned = assignedCount === 0
    const hasWarning = assignment.warningCount > 0
    return {
      key: `${detail.id}-${assignment.workShiftId ?? assignment.id}`,
      periodId: detail.id,
      date: assignment.date,
      dateLabel: formatDateLabel(assignment.date),
      shiftCode: assignment.shiftCode,
      shiftName: getShiftDefinition(assignment.shiftCode)?.name ?? assignment.shiftCode,
      assignedCount,
      unassigned,
      hasWarning,
      path: hasWarning || unassigned ? `/manager/schedules/${detail.id}?tab=warnings` : `/manager/schedules/${detail.id}`,
    } satisfies UpcomingShiftRow
  }))
  rows.sort((first, second) => first.date.localeCompare(second.date) || first.shiftCode.localeCompare(second.shiftCode))
  return rows.slice(0, limit)
}

export const pickScheduleAlerts = (details: ScheduleDetail[], limit: number): ScheduleAlertRow[] => {
  const rank = (severity: string) => severity === 'HIGH' ? 0 : severity === 'MEDIUM' ? 1 : 2
  const alerts = details.flatMap((detail) => detail.warnings.map((warning) => ({
    id: warning.id,
    periodId: detail.id,
    title: alertTitle(warning.type),
    detail: warning.content,
    severity: alertSeverityLabel(warning.severity),
    path: `/manager/schedules/${detail.id}?tab=warnings`,
  } satisfies ScheduleAlertRow)))
  alerts.sort((first, second) => rank(first.severity === 'Cao' ? 'HIGH' : first.severity === 'Trung bình' ? 'MEDIUM' : 'LOW') - rank(second.severity === 'Cao' ? 'HIGH' : second.severity === 'Trung bình' ? 'MEDIUM' : 'LOW'))
  return alerts.slice(0, limit)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- ManagerDashboard.test.tsx`

Workdir: `frontend`

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/manager/ManagerDashboard.helpers.ts frontend/src/pages/manager/ManagerDashboard.test.tsx
git commit -m "feat: add manager dashboard shift and alert helpers"
```

---

### Task 3: Wire ManagerDashboardPage to backend

**Files:**
- Modify: `frontend/src/pages/manager/ManagerDashboardPage.tsx`

- [ ] **Step 1: Confirm current behavior is mock-only**

Run: `npm.cmd test -- --run`

Workdir: `frontend`

Expected: PASS (baseline; page currently imports `@/mocks/dashboardData`, `@/mocks/managementData`, `@/mocks/schedules` and links to `schedule-demo-01`).

- [ ] **Step 2: Replace imports and data loading**

In `frontend/src/pages/manager/ManagerDashboardPage.tsx`, replace the three mock imports:

```tsx
import { currentDateLabel, managerDashboardData } from '@/mocks/dashboardData'
import { leaveRequestSeed, preferenceSeed, swapRequestSeed } from '@/mocks/managementData'
import { scheduleDetailSeed, scheduleSeed } from '@/mocks/schedules'
```

with:

```tsx
import { useEffect, useMemo, useState } from 'react'

import { apiConfig } from '@/config/api'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import { backendScheduleService } from '@/services/backendScheduleService'
import { leaveRequestService } from '@/services/leaveRequestService'
import { navigationSummaryService } from '@/services/navigationSummaryService'
import { preferenceService } from '@/services/preferenceService'
import { swapRequestService } from '@/services/swapRequestService'
import { getManagerActionItems, getManagerSummaryCards, pickScheduleAlerts, pickUpcomingShifts } from './ManagerDashboard.helpers'
import { getEmployeeWeekWindow } from '@/pages/employee/EmployeeSchedulePage.helpers'
import type { LeaveRequest, SwapRequest, WorkPreference } from '@/types/management'
import type { NavigationSummary } from '@/types/navigationSummary'
import type { ScheduleDetail, WorkSchedule } from '@/types/schedule'
```

Keep existing UI imports (`lucide-react` icons actually used, `Badge`, `Button`, `Card` parts, `PageHeader`, `useAuth`, `getShiftDefinition` only if still referenced, `Link`, `useNavigate`).

Add state and loading effect inside the component, right after `const navigate = useNavigate()`:

```tsx
const week = useMemo(() => getEmployeeWeekWindow(0), [])
const [isLoading, setIsLoading] = useState(() => !apiConfig.useMockApi)
const [loadError, setLoadError] = useState('')
const [employeeTotal, setEmployeeTotal] = useState(0)
const [periods, setPeriods] = useState<WorkSchedule[]>([])
const [details, setDetails] = useState<ScheduleDetail[]>([])
const [summary, setSummary] = useState<NavigationSummary | null>(null)
const [leaveHead, setLeaveHead] = useState<LeaveRequest | null>(null)
const [swapHead, setSwapHead] = useState<SwapRequest | null>(null)
const [preferenceHead, setPreferenceHead] = useState<WorkPreference | null>(null)

useEffect(() => {
  if (apiConfig.useMockApi) return
  let ignore = false
  const load = async () => {
    try {
      const [employees, navSummary, weekPeriods, leaves, swaps, preferences] = await Promise.all([
        backendEmployeeService.list({ status: 'ACTIVE', page: 1, pageSize: 1 }),
        navigationSummaryService.get(),
        backendScheduleService.listPeriods({ status: 'ALL', startDate: week.from, endDate: week.to }),
        leaveRequestService.list({ status: 'PENDING', page: 1, pageSize: 1 }),
        swapRequestService.list({ status: 'PENDING_APPROVAL', page: 1, pageSize: 1 }),
        preferenceService.list({ status: 'PENDING', page: 1, pageSize: 1 }),
      ])
      const weekDetails = await Promise.all(weekPeriods.slice(0, 5).map((period) => backendScheduleService.getScheduleDetail(period.id)))
      if (ignore) return
      setEmployeeTotal(employees.total)
      setSummary(navSummary)
      setPeriods(weekPeriods)
      setDetails(weekDetails)
      setLeaveHead(leaves.data[0] ?? null)
      setSwapHead(swaps.data[0] ?? null)
      setPreferenceHead(preferences.data[0] ?? null)
      setLoadError('')
    } catch (loadErr) {
      if (!ignore) setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải dữ liệu tổng quan.')
    } finally {
      if (!ignore) setIsLoading(false)
    }
  }
  void load()
  return () => { ignore = true }
}, [week.from, week.to])
```

Derive view models with `useMemo`:

```tsx
const weekShiftCount = useMemo(() => periods.reduce((total, period) => total + period.shiftCount, 0), [periods])
const draftDetails = useMemo(() => details.filter((detail) => detail.status === 'DRAFT'), [details])
const warningCount = useMemo(() => draftDetails.reduce((total, detail) => total + detail.warnings.length, 0), [draftDetails])
const summaryCards = useMemo(() => getManagerSummaryCards({
  employeeTotal,
  weekShiftCount,
  pendingLeave: summary?.pendingLeaveRequests ?? 0,
  pendingSwap: summary?.pendingSwapRequests ?? 0,
  pendingPreference: summary?.pendingPreferences ?? 0,
  warningCount,
}), [employeeTotal, weekShiftCount, summary, warningCount])
const upcomingShifts = useMemo(() => pickUpcomingShifts(details, 3), [details])
const alerts = useMemo(() => pickScheduleAlerts(details, 3), [details])
const draftPeriod = useMemo(() => {
  const first = periods.find((period) => period.status === 'DRAFT')
  return first ? { id: first.id, name: first.name } : null
}, [periods])
const criticalWarning = useMemo(() => {
  const high = draftDetails.flatMap((detail) => detail.warnings.map((warning) => ({ ...warning, periodId: detail.id }))).find((warning) => warning.severity === 'HIGH')
  return high ? { periodId: high.periodId, employeeName: high.employeeName, content: high.content } : null
}, [draftDetails])
const actionItems = useMemo(() => getManagerActionItems({ draftPeriod, leave: leaveHead, swap: swapHead, preference: preferenceHead, criticalWarning }), [draftPeriod, leaveHead, swapHead, preferenceHead, criticalWarning])
```

- [ ] **Step 3: Replace mock render blocks**

Replace `managerDashboardData.summary.map` with `summaryCards.map`, rendering `item.context` instead of the old `trend` badge text. Replace the hardcoded upcoming-shift array with `upcomingShifts`, showing `row.dateLabel`, `row.shiftName` plus `${row.assignedCount} phân công` (or `Chưa phân công` when `row.unassigned`), with a red badge when `row.hasWarning || row.unassigned`. Replace `actionItems` construction with the derived `actionItems` (keep existing icons per index: `CalendarCheck2`, `Clock3`, `ArrowRight`, `Users`, `AlertTriangle`). Delete the entire "Yêu cầu đang chờ xử lý" card section that maps `managerDashboardData.requests` and change the alerts grid from `xl:grid-cols-2` to a single full-width card mapping `alerts` with empty state "Không có cảnh báo lịch.". Replace `currentDateLabel` with a locally computed today label:

```tsx
const todayLabel = new Date().toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
```

Render loading (`Đang tải dữ liệu tổng quan...`) and error banners in backend mode. Delete all imports and references to `managerDashboardData`, `currentDateLabel`, `leaveRequestSeed`, `preferenceSeed`, `swapRequestSeed`, `scheduleDetailSeed`, `scheduleSeed`, and any `schedule-demo-01` path. If `getShiftDefinition` is no longer referenced after the replacement, delete its import as well so `tsc -b` passes.

- [ ] **Step 4: Run tests, build, and lint**

Run in `frontend`:

```bash
npm.cmd test -- ManagerDashboard.test.tsx
```

Expected: PASS, 6 tests.

Run: `npm.cmd run build`

Expected: exit 0 (Vite chunk-size warning acceptable).

Run: `npm.cmd run lint`

Expected: 0 errors (existing `AuthContext.tsx` Fast Refresh warning acceptable).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/manager/ManagerDashboardPage.tsx frontend/src/pages/manager/ManagerDashboard.helpers.ts frontend/src/pages/manager/ManagerDashboard.test.tsx
git commit -m "feat: wire manager dashboard to backend data"
```

---

### Task 4: Employee dashboard helpers

**Files:**
- Create: `frontend/src/pages/employee/EmployeeDashboard.helpers.ts`
- Test: `frontend/src/pages/employee/EmployeeDashboard.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/employee/EmployeeDashboard.test.tsx` with this exact content:

```tsx
import { describe, expect, test } from 'vitest'

import { getEmployeeSummaryCards, pickNextShiftDays } from './EmployeeDashboard.helpers'

describe('EmployeeDashboard helpers', () => {
  test('builds summary cards from real schedule and pending counts', () => {
    const cards = getEmployeeSummaryCards({
      todayShiftName: 'Ngoai gio 2',
      nextShiftName: 'Truc sang',
      totalMinutes: 2520,
      pendingLeave: 1,
      pendingPreference: 0,
      pendingSwap: 1,
    })
    expect(cards).toEqual([
      { label: 'Ca làm hôm nay', value: 'Ngoai gio 2', detail: 'Hôm nay' },
      { label: 'Ca làm tiếp theo', value: 'Truc sang', detail: 'Sắp tới' },
      { label: 'Tổng giờ làm', value: '42h', detail: 'Tuần này' },
      { label: 'Yêu cầu chờ xử lý', value: '2', detail: '1 đổi ca, 1 nghỉ phép' },
    ])
  })

  test('falls back when no shifts exist', () => {
    const cards = getEmployeeSummaryCards({ todayShiftName: null, nextShiftName: null, totalMinutes: 0, pendingLeave: 0, pendingPreference: 0, pendingSwap: 0 })
    expect(cards[0]).toMatchObject({ value: '—' })
    expect(cards[2]).toMatchObject({ value: '0h' })
  })

  test('picks next shift days with real assignment ids', () => {
    const days = pickNextShiftDays([
      { id: '7', date: '2026-09-25', day: 'Thứ Sáu', shiftCode: 'THONG_TAM', shiftName: 'Thong tam', timeRange: '08:00:00 - 17:00:00', status: 'ASSIGNED', warning: '' },
      { id: '9', date: '2026-09-26', day: 'Thứ Bảy', shiftCode: 'TRUC_SANG', shiftName: 'Truc sang', timeRange: '08:00:00 - 16:00:00', status: 'ASSIGNED', warning: '' },
    ], 3)
    expect(days.map((day) => day.id)).toEqual(['7', '9'])
    expect(days[0]).toMatchObject({ date: '2026-09-25', shiftName: 'Thong tam' })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- EmployeeDashboard.test.tsx`

Workdir: `frontend`

Expected: FAIL with "Cannot find module './EmployeeDashboard.helpers'".

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/pages/employee/EmployeeDashboard.helpers.ts` with this exact content:

```ts
import type { EmployeeScheduleViewItem } from './EmployeeSchedulePage.helpers'

export interface EmployeeSummaryCard {
  label: string
  value: string
  detail: string
}

export const getEmployeeSummaryCards = ({
  todayShiftName,
  nextShiftName,
  totalMinutes,
  pendingLeave,
  pendingPreference,
  pendingSwap,
}: {
  todayShiftName: string | null
  nextShiftName: string | null
  totalMinutes: number
  pendingLeave: number
  pendingPreference: number
  pendingSwap: number
}): EmployeeSummaryCard[] => [
  { label: 'Ca làm hôm nay', value: todayShiftName ?? '—', detail: 'Hôm nay' },
  { label: 'Ca làm tiếp theo', value: nextShiftName ?? '—', detail: 'Sắp tới' },
  { label: 'Tổng giờ làm', value: `${Math.round(totalMinutes / 60)}h`, detail: 'Tuần này' },
  { label: 'Yêu cầu chờ xử lý', value: String(pendingLeave + pendingPreference + pendingSwap), detail: `${pendingSwap} đổi ca, ${pendingLeave} nghỉ phép` },
]

export const pickNextShiftDays = (items: EmployeeScheduleViewItem[], limit: number): EmployeeScheduleViewItem[] => {
  const sorted = [...items].sort((first, second) => first.date.localeCompare(second.date))
  const seen = new Set<string>()
  const days: EmployeeScheduleViewItem[] = []
  for (const item of sorted) {
    if (seen.has(item.date)) continue
    seen.add(item.date)
    days.push(item)
    if (days.length >= limit) break
  }
  return days
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- EmployeeDashboard.test.tsx`

Workdir: `frontend`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/employee/EmployeeDashboard.helpers.ts frontend/src/pages/employee/EmployeeDashboard.test.tsx
git commit -m "feat: add employee dashboard helpers"
```

---

### Task 5: Wire EmployeeDashboardPage to backend

**Files:**
- Modify: `frontend/src/pages/employee/EmployeeDashboardPage.tsx`

- [ ] **Step 1: Confirm current behavior is mock-only**

Run: `npm.cmd test -- --run`

Workdir: `frontend`

Expected: PASS (baseline; page imports `employeeDashboardData` only).

- [ ] **Step 2: Replace data loading**

In `frontend/src/pages/employee/EmployeeDashboardPage.tsx`, replace:

```tsx
import { employeeDashboardData } from '@/mocks/dashboardData'
```

with:

```tsx
import { useEffect, useMemo, useState } from 'react'

import { apiConfig } from '@/config/api'
import { employeeScheduleService } from '@/services/employeeScheduleService'
import { navigationSummaryService } from '@/services/navigationSummaryService'
import { notificationService } from '@/services/notificationService'
import { adaptBackendEmployeeSchedule, getEmployeeWeekWindow } from './EmployeeSchedulePage.helpers'
import { getEmployeeSummaryCards, pickNextShiftDays } from './EmployeeDashboard.helpers'
import type { NavigationSummary } from '@/types/navigationSummary'
import type { NotificationItem } from '@/types/management'
import type { EmployeeScheduleViewItem } from './EmployeeSchedulePage.helpers'
```

Add state and effect after `const navigate = useNavigate()`:

```tsx
const week = useMemo(() => getEmployeeWeekWindow(0), [])
const todayInput = useMemo(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}, [])
const [isLoading, setIsLoading] = useState(() => !apiConfig.useMockApi)
const [loadError, setLoadError] = useState('')
const [scheduleItems, setScheduleItems] = useState<EmployeeScheduleViewItem[]>([])
const [summary, setSummary] = useState<NavigationSummary | null>(null)
const [notices, setNotices] = useState<NotificationItem[]>([])

useEffect(() => {
  if (apiConfig.useMockApi) return
  let ignore = false
  const load = async () => {
    try {
      const [rawSchedule, navSummary, noticePage] = await Promise.all([
        employeeScheduleService.list(week),
        navigationSummaryService.get(),
        notificationService.list({ page: 1, pageSize: 3 }),
      ])
      if (ignore) return
      setScheduleItems(adaptBackendEmployeeSchedule(rawSchedule))
      setSummary(navSummary)
      setNotices(noticePage.data)
      setLoadError('')
    } catch (loadErr) {
      if (!ignore) setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải dữ liệu tổng quan.')
    } finally {
      if (!ignore) setIsLoading(false)
    }
  }
  void load()
  return () => { ignore = true }
}, [week])
```

Derive view models:

```tsx
const todayShiftName = scheduleItems.find((item) => item.date === todayInput)?.shiftName ?? null
const nextShiftName = scheduleItems.find((item) => item.date > todayInput)?.shiftName ?? null
const totalMinutes = scheduleItems.reduce((total, item) => total + (getShiftDefinition(item.shiftCode)?.workMinutes ?? 0), 0)
const summaryCards = getEmployeeSummaryCards({
  todayShiftName,
  nextShiftName,
  totalMinutes,
  pendingLeave: summary?.pendingLeaveRequests ?? 0,
  pendingPreference: summary?.pendingPreferences ?? 0,
  pendingSwap: summary?.pendingSwapRequests ?? 0,
})
const nextShiftDays = pickNextShiftDays(scheduleItems.filter((item) => item.date >= todayInput), 3)
```

- [ ] **Step 3: Replace mock render blocks**

Render `summaryCards` instead of `employeeDashboardData.summary`, linking cards to `/employee/schedule?view=today`, `/employee/schedule` (next shift: link `/employee/schedule?assignmentId={realId}` when a next item exists, plain `/employee/schedule` otherwise), `/employee/schedule?view=week`, and `/employee/leave-requests?status=PENDING`. Render `nextShiftDays` instead of `employeeDashboardData.nextShifts` with real `assignmentId` links and empty state "Bạn chưa có lịch làm việc trong tuần này.". Render `notices` instead of `employeeDashboardData.notifications` with real `notificationId` links and empty state "Không có thông báo mới.". Replace the hardcoded `Tuần 38 • 16/09/2026` badge with the computed range `${week.from} - ${week.to}`. Delete the `employeeDashboardData` import. If `formatShiftRangeLabel` is no longer referenced after the replacement, delete its import as well so `tsc -b` passes. Render loading and error banners in backend mode. Keep quick actions unchanged.

- [ ] **Step 4: Run tests, build, and lint**

Run in `frontend`:

```bash
npm.cmd test -- EmployeeDashboard.test.tsx EmployeeSchedulePage.test.tsx employeeScheduleService.test.ts
```

Expected: PASS.

Run: `npm.cmd run build`

Expected: exit 0 (Vite chunk-size warning acceptable).

Run: `npm.cmd run lint`

Expected: 0 errors (existing `AuthContext.tsx` Fast Refresh warning acceptable).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/employee/EmployeeDashboardPage.tsx frontend/src/pages/employee/EmployeeDashboard.helpers.ts frontend/src/pages/employee/EmployeeDashboard.test.tsx
git commit -m "feat: wire employee dashboard to backend data"
```

---

### Task 6: Final verification

**Files:**
- No source changes unless verification exposes failures.

- [ ] **Step 1: Run frontend tests**

Run:

```powershell
npm.cmd test -- --run
```

Workdir: `frontend`

Expected: all test files pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Workdir: `frontend`

Expected: exit 0. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 3: Run frontend lint**

Run:

```powershell
npm.cmd run lint
```

Workdir: `frontend`

Expected: 0 errors. Existing Fast Refresh warning in `AuthContext.tsx` is acceptable.

- [ ] **Step 4: Inspect diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only intended dashboard files changed plus the two spec/plan docs. Do not stage or commit unless the user explicitly asks.

---

## Self-Review Notes

- Spec coverage: manager summary, upcoming shifts, action center, alerts, pending-card removal, employee summary, weekly schedule, notifications, stale links and dates are each mapped to Tasks 1-5. No backend changes, matching the spec approach.
- Placeholder scan: no TBD/TODO or vague instructions; every code step contains complete code with exact file paths, and every test step contains exact assertions and commands.
- Type consistency: helper input shapes use plain structural types compatible with `LeaveRequest`, `SwapRequest`, `WorkPreference`, `ScheduleDetail`, `WorkSchedule`, `NavigationSummary`, `NotificationItem`, and `EmployeeScheduleViewItem`; `pickUpcomingShifts`/`pickScheduleAlerts` accept `ScheduleDetail[]` as produced by `backendScheduleService.getScheduleDetail`; `getEmployeeWeekWindow` is reused from `EmployeeSchedulePage.helpers` with its existing `(weekOffset, baseDate)` signature.

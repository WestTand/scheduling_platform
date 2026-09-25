# Schedule Edit Backend Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish `/manager/schedules/:id/edit` so backend mode clearly edits real assignments and blocks invalid mutations for immutable schedules.

**Architecture:** Keep changes local to `ScheduleEditPage`. The page owns load/reload state, splits success/error notices, and derives editability from schedule status. No backend API changes are required.

**Tech Stack:** React 19, TypeScript, Vitest static render tests, existing backend schedule and employee services.

---

## File Structure

- Create: `frontend/src/pages/manager/ScheduleEditPage.test.tsx` for static render tests.
- Modify: `frontend/src/pages/manager/ScheduleEditPage.tsx` for backend-mode copy, notice styling, locked-state behavior, and detail refresh.

## Task 1: Backend Mode Copy and Locked-State UI

**Files:**
- Create: `frontend/src/pages/manager/ScheduleEditPage.test.tsx`
- Modify: `frontend/src/pages/manager/ScheduleEditPage.tsx`

- [ ] **Step 1: Write failing static render tests**

Create `frontend/src/pages/manager/ScheduleEditPage.test.tsx`:

```tsx
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const createStorage = (): Storage => {
  let values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear: () => { values = new Map() },
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key) },
    setItem: (key, value) => { values.set(key, value) },
  }
}

const periodSchedule = (status: string) => ({
  periodId: 1,
  periodName: 'Tuần mẫu tháng 09/2026',
  status,
  startDate: '2026-09-21',
  endDate: '2026-09-27',
  workShifts: [],
})

const employees = [{ id: 1, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', email: 'emp001@example.com', phone: '0900000001', status: 'ACTIVE', account: null }]

describe('ScheduleEditPage', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('shows backend mode copy instead of mock editing copy', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(periodSchedule('DRAFT')), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(employees), { headers: { 'Content-Type': 'application/json' } })))

    const { ScheduleEditPage } = await import('./ScheduleEditPage')
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/manager/schedules/1/edit']}>
        <Routes><Route path="/manager/schedules/:scheduleId/edit" element={<ScheduleEditPage />} /></Routes>
      </MemoryRouter>,
    )

    expect(html).toContain('Backend mode: chỉnh sửa phân công trực tiếp')
    expect(html).not.toContain('Chế độ chỉnh sửa mock')
    expect(html).not.toContain('Lưu bản nháp')
    expect(html).not.toContain('Gửi duyệt')
  })

  it('shows locked editing banner for published backend schedules', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(periodSchedule('PUBLISHED')), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(employees), { headers: { 'Content-Type': 'application/json' } })))

    const { ScheduleEditPage } = await import('./ScheduleEditPage')
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/manager/schedules/1/edit']}>
        <Routes><Route path="/manager/schedules/:scheduleId/edit" element={<ScheduleEditPage />} /></Routes>
      </MemoryRouter>,
    )

    expect(html).toContain('Lịch đã công bố nên không thể chỉnh sửa phân công.')
    expect(html).toContain('disabled=""')
  })
})
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleEditPage.test.tsx
```

Expected: FAIL because backend-mode copy is not present and mock buttons/text still render.

- [ ] **Step 3: Implement backend-mode copy and locked-state UI**

In `frontend/src/pages/manager/ScheduleEditPage.tsx`:

1. Add derived state after `isLoading`:

```ts
  const isBackendMode = !apiConfig.useMockApi
  const isEditingLocked = isBackendMode && (detail?.status === 'PUBLISHED' || detail?.status === 'CANCELLED')
  const lockedMessage = detail?.status === 'PUBLISHED'
    ? 'Lịch đã công bố nên không thể chỉnh sửa phân công.'
    : detail?.status === 'CANCELLED'
      ? 'Lịch đã hủy nên không thể chỉnh sửa phân công.'
      : ''
```

2. Replace the notice render:

```tsx
      {notice && <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</div>}
      {lockedMessage && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{lockedMessage}</div>}
```

3. Change action buttons block:

```tsx
          <div className="flex flex-wrap gap-3">
            <Button onClick={openCreate} disabled={isEditingLocked}><CalendarDays className="mr-2 h-4 w-4" />Thêm phân công</Button>
            {apiConfig.useMockApi && <Button variant="outline" onClick={() => handleSubmitStatus('DRAFT')}><Save className="mr-2 h-4 w-4" />Lưu bản nháp</Button>}
            {apiConfig.useMockApi && <Button variant="outline" onClick={() => handleSubmitStatus('PENDING_APPROVAL')}><Send className="mr-2 h-4 w-4" />Gửi duyệt</Button>}
          </div>
```

4. Change mode label line:

```tsx
            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-500">{apiConfig.useMockApi ? 'Chế độ chỉnh sửa mock' : 'Backend mode: chỉnh sửa phân công trực tiếp'}</div>
```

5. Disable row actions:

```tsx
                  <Button variant="outline" size="sm" onClick={() => openEdit(assignment)} disabled={isEditingLocked}>Sửa</Button>
                  <Button variant="outline" size="sm" onClick={() => setDeleteId(assignment.id)} disabled={isEditingLocked} className="text-red-600"> <Trash2 className="mr-2 h-4 w-4" />Xóa</Button>
```

- [ ] **Step 4: Run test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleEditPage.test.tsx
```

Expected: PASS.

## Task 2: Notice Severity and Backend Detail Refresh

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleEditPage.tsx`
- Modify: `frontend/src/pages/manager/ScheduleEditPage.test.tsx`

- [ ] **Step 1: Add failing test for error styling**

Append this test to `ScheduleEditPage.test.tsx`:

```tsx
  it('renders backend load failures as red error notices', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Backend failure' }), { status: 500, headers: { 'Content-Type': 'application/json' } })))

    const { ScheduleEditPage } = await import('./ScheduleEditPage')
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/manager/schedules/1/edit']}>
        <Routes><Route path="/manager/schedules/:scheduleId/edit" element={<ScheduleEditPage />} /></Routes>
      </MemoryRouter>,
    )

    expect(html).toContain('Backend failure')
    expect(html).toContain('border-red-200')
  })
```

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleEditPage.test.tsx
```

Expected: FAIL because errors currently render in neutral/blue contexts or the page not-found card.

- [ ] **Step 3: Implement notice severity and refresh helper**

In `ScheduleEditPage.tsx`:

1. Replace `const [notice, setNotice] = useState('')` with:

```ts
  const [notice, setNotice] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
```

2. Add helper after `findEmployeeId`:

```ts
  const showSuccess = (message: string) => setNotice({ type: 'success', message })
  const showError = (message: string) => setNotice({ type: 'error', message })

  const refreshScheduleDetail = async () => {
    if (!scheduleId) return null
    const refreshed = await backendScheduleService.getScheduleDetail(scheduleId)
    setBackendResult({ scheduleId, detail: refreshed, error: '' })
    setAssignments(refreshed.assignments)
    return refreshed
  }
```

3. Replace all `setNotice('success text')` for success paths with `showSuccess('success text')`.

4. Replace all `setNotice(error text)` for validation/error paths with `showError(error text)`.

5. In load catch, keep `setBackendResult(...)` but also call `showError(...)` with the same message.

6. Replace backend save/delete refresh blocks so after create/update/delete they call `await refreshScheduleDetail()` and show success.

7. Replace notice render with:

```tsx
      {notice && <div className={`rounded-xl border px-4 py-3 text-sm ${notice.type === 'error' ? 'border-red-200 bg-red-50 text-red-700' : 'border-blue-200 bg-blue-50 text-blue-800'}`}>{notice.message}</div>}
```

- [ ] **Step 4: Run page test and verify GREEN**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleEditPage.test.tsx
```

Expected: PASS.

## Task 3: Verification

**Files:**
- No code changes unless verification reveals a defect.

- [ ] **Step 1: Run focused frontend tests**

Run:

```powershell
npm.cmd test -- src/pages/manager/ScheduleEditPage.test.tsx
```

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Expected: PASS. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 3: Run frontend lint**

Run:

```powershell
npm.cmd run lint
```

Expected: no lint errors. Existing `AuthContext.tsx` Fast Refresh warning may remain.

- [ ] **Step 4: Manual smoke**

Open backend mode:

1. Login as manager.
2. Visit `/manager/schedules/1/edit`.
3. Confirm no JDBC/500 message appears.
4. Confirm backend-mode label is visible.
5. If schedule is `PUBLISHED` or `CANCELLED`, confirm mutation buttons are disabled and locked banner is visible.

## Self-Review

- Spec coverage: backend copy, locked statuses, hidden mock status buttons, notice severity, and detail refresh all map to tasks.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: uses existing `ScheduleDetail.status`, `ScheduleStatus` string values, and existing backend services.

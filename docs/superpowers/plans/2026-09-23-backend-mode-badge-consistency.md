# Backend Mode Badge Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent sidebar badges from showing mock seed counts while the frontend is running in backend mode.

**Architecture:** Keep the fix centralized in `Sidebar.getBadgeCount`. In mock mode, existing seed-based demo counts remain unchanged; in backend mode, every badge count currently derived from mock arrays returns `0` unless a backend-backed count source exists.

**Tech Stack:** React 19, React Router, Vite, Vitest, TypeScript.

---

## File Structure

- Modify: `frontend/src/components/layout/Sidebar.tsx`
  - Responsibility: render navigation links and compute badge counts.
  - Change: add backend-mode guards for all remaining mock-seed-derived badges.
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`
  - Responsibility: regression-test sidebar badge rendering in backend mode.
  - Change: add items and assertions for `Nguyện vọng`, `Thông báo`, and `Quản lý lịch`.

---

### Task 1: Add Backend-Mode Regression Coverage For Remaining Mock Badges

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`

- [ ] **Step 1: Write failing tests for remaining backend-mode badges**

Update the test file so `items` includes all sidebar entries whose counts come from mock seeds, then add assertions for the remaining labels. The full intended test file content should be:

```tsx
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Role, type User } from '@/types/auth'

const managerUser: User = {
  id: 'manager-1',
  name: 'Test Manager',
  email: 'manager@example.com',
  role: Role.MANAGER,
}

const employeeUser: User = {
  id: 'employee-1',
  name: 'Test Employee',
  email: 'employee@example.com',
  role: Role.EMPLOYEE,
}

const items = [
  { label: 'Quản lý lịch', href: '/manager/schedules', icon: 'CalendarRange' },
  { label: 'Ngày nghỉ', href: '/manager/leave-requests', icon: 'CalendarX2' },
  { label: 'Nguyện vọng', href: '/manager/preferences', icon: 'ClipboardList' },
  { label: 'Yêu cầu đổi ca', href: '/manager/swap-requests', icon: 'ArrowLeftRight' },
  { label: 'Thông báo', href: '/manager/notifications', icon: 'Bell' },
]

describe('Sidebar', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('does not show mock request badges in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={managerUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Ngày nghỉ<\/span><span[^>]*>1<\/span>/)
    expect(html).not.toMatch(/Yêu cầu đổi ca<\/span><span[^>]*>1<\/span>/)
  })

  it('does not show mock preference, notification, or schedule badges in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={managerUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Nguyện vọng<\/span><span[^>]*>1<\/span>/)
    expect(html).not.toMatch(/Thông báo<\/span><span[^>]*>1<\/span>/)
    expect(html).not.toMatch(/Quản lý lịch<\/span><span[^>]*>1<\/span>/)
  })

  it('still hides manager schedule badges for employees in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={employeeUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Quản lý lịch<\/span><span[^>]*>1<\/span>/)
  })
})
```

- [ ] **Step 2: Run the sidebar test to verify it fails**

Run:

```powershell
npm.cmd test -- src/components/layout/Sidebar.test.tsx
```

Expected: the new backend-mode test fails because `Nguyện vọng`, `Thông báo`, or `Quản lý lịch` still render mock badge counts.

- [ ] **Step 3: Commit nothing yet**

Do not commit while the test is red.

---

### Task 2: Hide Remaining Mock Badges In Backend Mode

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Implement the minimal backend-mode guard**

Update `getBadgeCount` so every mock-derived count returns `0` in backend mode. The function should become:

```tsx
const getBadgeCount = (label: string, role: Role) => {
  if (label === 'Ngày nghỉ') {
    if (!apiConfig.useMockApi) {
      return 0
    }
    return leaveRequestSeed.filter((item) => item.status === 'PENDING').length
  }
  if (label === 'Nguyện vọng') {
    if (!apiConfig.useMockApi) {
      return 0
    }
    return preferenceSeed.filter((item) => item.status === 'PENDING').length
  }
  if (label === 'Yêu cầu đổi ca') {
    if (!apiConfig.useMockApi) {
      return 0
    }
    return swapRequestSeed.filter((item) => item.status === 'PENDING_TARGET' || item.status === 'PENDING_APPROVAL').length
  }
  if (label === 'Thông báo') {
    if (!apiConfig.useMockApi) {
      return 0
    }
    return notificationSeed.filter((item) => !item.isRead).length
  }
  if (role === Role.MANAGER && label === 'Quản lý lịch') {
    if (!apiConfig.useMockApi) {
      return 0
    }
    return scheduleSeed.filter((item) => item.status === 'PENDING_APPROVAL').length
  }
  return 0
}
```

- [ ] **Step 2: Run the sidebar regression test**

Run:

```powershell
npm.cmd test -- src/components/layout/Sidebar.test.tsx
```

Expected: all sidebar tests pass.

- [ ] **Step 3: Run full frontend verification**

Run:

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Expected:
- Tests pass.
- Lint has 0 errors. Existing `AuthContext.tsx` Fast Refresh warning may remain.
- Build succeeds. Existing chunk-size warning may remain.

- [ ] **Step 4: Review diff**

Run:

```powershell
git diff -- frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git status --short
```

Expected: only sidebar implementation/test changes are staged or unstaged, plus any pre-existing unrelated untracked docs remain untouched.

- [ ] **Step 5: Commit the implementation**

Run:

```powershell
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git commit -m "fix: hide remaining mock badges in backend mode"
```

Expected: commit succeeds.

---

## Final Verification

- [ ] Run `npm.cmd test` in `frontend/`.
- [ ] Run `npm.cmd run lint` in `frontend/`.
- [ ] Run `npm.cmd run build` in `frontend/`.
- [ ] Push branch `phase9h/backend-mode-badge-consistency` after all verification passes.

import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Role, type User } from '@/types/auth'
import { AUTH_STORAGE_KEYS } from '@/services/authStorage'

// NOTE: @testing-library/react is NOT listed in frontend/package.json, so async
// useEffect badge rendering cannot be asserted via render + waitFor here.
// Backend badge counts are covered at the service level below (fetch URL + fields);
// static-markup tests cover the loading/failure->empty case (no badges on first render).

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
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

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

    expect(html).not.toMatch(/Ngày nghỉ<\/span><span[^>]*>\d+<\/span>/)
    expect(html).not.toMatch(/Yêu cầu đổi ca<\/span><span[^>]*>\d+<\/span>/)
  })

  it('does not show mock preference, notification, or schedule badges in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={managerUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Nguyện vọng<\/span><span[^>]*>\d+<\/span>/)
    expect(html).not.toMatch(/Thông báo<\/span><span[^>]*>\d+<\/span>/)
    expect(html).not.toMatch(/Quản lý lịch<\/span><span[^>]*>\d+<\/span>/)
  })

  it('still hides manager schedule badges for employees in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={employeeUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Quản lý lịch<\/span><span[^>]*>\d+<\/span>/)
  })

  it('loads backend summary counts used for badges', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({
      pendingLeaveRequests: 1,
      pendingPreferences: 2,
      pendingSwapRequests: 3,
      unreadNotifications: 4,
      pendingSchedules: 0,
    }), { headers: { 'Content-Type': 'application/json' } }))

    const { navigationSummaryService } = await import('@/services/navigationSummaryService')
    const summary = await navigationSummaryService.get()

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/me/navigation-summary', expect.any(Object))
    expect(summary).toEqual({
      pendingLeaveRequests: 1,
      pendingPreferences: 2,
      pendingSwapRequests: 3,
      unreadNotifications: 4,
      pendingSchedules: 0,
    })
  })
})

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
})

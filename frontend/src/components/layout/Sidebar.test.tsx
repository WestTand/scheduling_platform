import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Role, type User } from '@/types/auth'

const user: User = {
  id: 'manager-1',
  name: 'Test Manager',
  email: 'manager@example.com',
  role: Role.MANAGER,
}

const items = [
  { label: 'Ngày nghỉ', href: '/manager/leave-requests', icon: 'CalendarX2' },
  { label: 'Yêu cầu đổi ca', href: '/manager/swap-requests', icon: 'ArrowLeftRight' },
]

describe('Sidebar', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('does not show mock swap request badges in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={user} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Yêu cầu đổi ca<\/span><span[^>]*>1<\/span>/)
  })

  it('does not show mock leave request badges in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { Sidebar } = await import('./Sidebar')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <Sidebar items={items} user={user} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
      </MemoryRouter>,
    )

    expect(html).not.toMatch(/Ngày nghỉ<\/span><span[^>]*>1<\/span>/)
  })
})

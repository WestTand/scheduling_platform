import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendNotificationService } from '@/services/backendNotificationService'
import { AUTH_STORAGE_KEYS } from '@/services/authStorage'

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

const jsonResponse = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

describe('backendNotificationService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('lists notifications with read filter and client-side pagination', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { id: 1, title: 'A', content: 'Leave approved', notificationType: 'LEAVE_APPROVED', read: false, createdAt: '2026-09-23T08:00:00' },
      { id: 2, title: 'B', content: 'Schedule published', notificationType: 'SCHEDULE_PUBLISHED', read: true, createdAt: '2026-09-23T09:00:00' },
    ]))

    const result = await backendNotificationService.list({ page: 1, pageSize: 1, search: 'leave', type: 'ALL', status: 'UNREAD' })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/employee/notifications?read=false', expect.any(Object))
    expect(result.total).toBe(1)
    expect(result.data[0]?.title).toBe('A')
  })

  test('marks one notification read and marks all read', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ id: 1, title: 'A', content: 'C', notificationType: 'GENERAL', read: true, createdAt: '2026-09-23T08:00:00' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    const item = await backendNotificationService.markRead('1')
    await backendNotificationService.markAllRead()

    expect(item.isRead).toBe(true)
    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/employee/notifications/1/read', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/employee/notifications/read-all', expect.objectContaining({ method: 'PATCH' }))
  })
})

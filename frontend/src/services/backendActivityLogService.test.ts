import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendActivityLogService } from '@/services/backendActivityLogService'
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

describe('backendActivityLogService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('lists activity logs with filters and client-side search', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { id: 1, accountId: 10, username: 'manager', action: 'APPROVE', entityType: 'leave_requests', entityId: 2, oldValueJson: null, newValueJson: '{"status":"APPROVED"}', createdAt: '2026-09-23T08:00:00', ipAddress: null },
      { id: 2, accountId: 10, username: 'manager', action: 'LOCK', entityType: 'employees', entityId: 3, oldValueJson: null, newValueJson: '{}', createdAt: '2026-09-23T09:00:00', ipAddress: null },
    ]))

    const result = await backendActivityLogService.list({ page: 1, pageSize: 5, search: 'nghỉ phép', action: 'APPROVE', startDate: '2026-09-01', endDate: '2026-09-30' })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/admin/activity-logs?action=APPROVE&from=2026-09-01&to=2026-09-30', expect.any(Object))
    expect(result.total).toBe(1)
    expect(result.data[0]?.target).toBe('Đơn nghỉ phép #2')
  })
})

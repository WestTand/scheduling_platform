import { beforeEach, describe, expect, test, vi } from 'vitest'
import { employeeScheduleService } from './employeeScheduleService'
import { AUTH_STORAGE_KEYS } from './authStorage'

const jsonResponse = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

describe('employeeScheduleService', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      get length() { return store.size },
      clear: () => store.clear(),
      getItem: (key: string) => store.get(key) ?? null,
      key: (index: number) => Array.from(store.keys())[index] ?? null,
      removeItem: (key: string) => { store.delete(key) },
      setItem: (key: string, value: string) => { store.set(key, value) },
    })
    vi.stubGlobal('sessionStorage', {
      get length() { return 0 },
      clear: () => {},
      getItem: () => null,
      key: () => null,
      removeItem: () => {},
      setItem: () => {},
    })
    sessionStorage.setItem?.(AUTH_STORAGE_KEYS.TOKEN, 'token')
    localStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('loads employee schedule from backend date window', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { assignmentId: 1, workDate: '2026-09-25', shiftTypeName: 'Thong tam', shiftStartTime: '08:00:00', shiftEndTime: '17:00:00' },
    ]))

    const result = await employeeScheduleService.list({ from: '2026-09-22', to: '2026-09-28' })

    expect(String(vi.mocked(fetch).mock.calls[0]?.[0])).toContain('/api/employee/schedule')
    expect(String(vi.mocked(fetch).mock.calls[0]?.[0])).toContain('from=2026-09-22')
    expect(result).toHaveLength(1)
  })
})

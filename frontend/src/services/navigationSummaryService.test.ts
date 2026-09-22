import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

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

describe('navigationSummaryService', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  test('loads backend navigation summary counts', async () => {
    const { navigationSummaryService } = await import('@/services/navigationSummaryService')
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({
      pendingLeaveRequests: 1,
      pendingPreferences: 2,
      pendingSwapRequests: 3,
      unreadNotifications: 4,
      pendingSchedules: 0,
    }), { headers: { 'Content-Type': 'application/json' } }))

    const result = await navigationSummaryService.get()

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/me/navigation-summary', expect.any(Object))
    expect(result).toEqual({
      pendingLeaveRequests: 1,
      pendingPreferences: 2,
      pendingSwapRequests: 3,
      unreadNotifications: 4,
      pendingSchedules: 0,
    })
  })

  test('returns seed-derived counts in mock mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    const { navigationSummaryService: mockService } = await import('@/services/navigationSummaryService')

    const result = await mockService.get()

    expect(fetch).not.toHaveBeenCalled()
    expect(result).toEqual({
      pendingLeaveRequests: 1,
      pendingPreferences: 1,
      pendingSwapRequests: 1,
      unreadNotifications: 2,
      pendingSchedules: 0,
    })
  })
})

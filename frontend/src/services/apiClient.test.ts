import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { requestJson } from '@/services/apiClient'
import { AUTH_STORAGE_KEYS } from '@/services/authStorage'

const jsonResponse = (body: unknown, init: ResponseInit = {}) => {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
}

const createStorage = (): Storage => {
  let values = new Map<string, string>()

  return {
    get length() {
      return values.size
    },
    clear: () => {
      values = new Map()
    },
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => {
      values.delete(key)
    },
    setItem: (key, value) => {
      values.set(key, value)
    },
  }
}

describe('requestJson', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    localStorage.clear()
    sessionStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('refreshes an expired access token and retries the original authenticated request once', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'expired-token')
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'fresh-token', account: {} }))
      .mockResolvedValueOnce(jsonResponse({ value: 'ok' }))

    const result = await requestJson<{ value: string }>('/api/admin/leave-requests')
    const firstRequest = fetchMock.mock.calls[0]?.[1]
    const refreshRequest = fetchMock.mock.calls[1]?.[1]
    const retryRequest = fetchMock.mock.calls[2]?.[1]

    expect(result).toEqual({ value: 'ok' })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/leave-requests')
    expect((firstRequest?.headers as Headers).get('Authorization')).toBe('Bearer expired-token')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/auth/refresh')
    expect(refreshRequest).toEqual(expect.objectContaining({ method: 'POST', credentials: 'include' }))
    expect(fetchMock.mock.calls[2]?.[0]).toBe('http://localhost:8080/api/admin/leave-requests')
    expect((retryRequest?.headers as Headers).get('Authorization')).toBe('Bearer fresh-token')
    expect(sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBe('fresh-token')
  })
})

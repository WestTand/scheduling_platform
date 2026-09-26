import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { requestBlob, requestJson } from '@/services/apiClient'
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

  test('clears stored auth when token refresh fails after an authenticated 401', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'expired-token')
    sessionStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: '1' }))
    localStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'stale-token')
    localStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: '2' }))
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ message: 'Invalid refresh token' }, { status: 401 }))

    await expect(requestJson('/api/employee/leave-requests')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBeNull()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.USER)).toBeNull()
  })

  test('stores refreshed token in local storage when current token is local scoped', async () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'expired-local-token')
    localStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: '1' }))
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'fresh-local-token', account: {} }))
      .mockResolvedValueOnce(jsonResponse({ value: 'ok' }))

    await requestJson('/api/admin/schedule-periods')

    expect(localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBe('fresh-local-token')
    expect(sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBeNull()
  })

  test('does not clear stored auth for non-401 API errors', async () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'valid-token')
    localStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: '1' }))
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ message: 'Bad request' }, { status: 400 }))

    await expect(requestJson('/api/admin/schedule-periods/generate', { method: 'POST', body: {} })).rejects.toThrow('Bad request')

    expect(localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBe('valid-token')
  })

  test('stores refreshed token in session storage when the active user is session scoped', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: '1' }))
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'fresh-session-token', account: {} }))
      .mockResolvedValueOnce(jsonResponse({ value: 'ok' }))

    await requestJson('/api/employee/notifications')

    expect(sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBe('fresh-session-token')
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)).toBeNull()
  })
})

describe('requestBlob', () => {
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

  test('returns blob and filename for binary download responses', async () => {
    const bytes = new Uint8Array([1, 2, 3])
    vi.mocked(fetch).mockResolvedValueOnce(new Response(bytes, {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="schedule-period-48.xlsx"',
      },
    }))

    const result = await requestBlob('/api/admin/schedule-periods/48/exports/schedule.xlsx')

    expect(result.filename).toBe('schedule-period-48.xlsx')
    expect(result.blob.size).toBe(3)
  })

  test('throws ApiError with backend message for failed downloads', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ message: 'Schedule period not found' }, { status: 404 }))

    await expect(requestBlob('/api/admin/schedule-periods/999/exports/schedule.xlsx')).rejects.toMatchObject({
      status: 404,
      message: 'Schedule period not found',
    })
  })
})

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { AUTH_STORAGE_KEYS } from '@/services/authStorage'
import { restoreStoredUser } from '@/services/authService'
import { Role } from '@/types/auth'

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

describe('restoreStoredUser', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('prefers session user over stale local user', () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: 'local', name: 'Local', email: 'local@example.com', role: Role.MANAGER }))
    sessionStorage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify({ id: 'session', name: 'Session', email: 'session@example.com', role: Role.EMPLOYEE }))

    expect(restoreStoredUser()?.id).toBe('session')
  })
})

describe('authService.login in backend mode', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  test('resolves the signed-in user full name from the me endpoint', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
      if (url === 'http://localhost:8080/api/auth/login') {
        return Promise.resolve(new Response(JSON.stringify({
          accessToken: 'fresh-token',
          tokenType: 'Bearer',
          expiresInSeconds: 900,
          account: { id: 2, username: 'emp001@example.com', role: 'EMPLOYEE' },
        }), { headers: { 'Content-Type': 'application/json' } }))
      }

      return Promise.resolve(new Response(JSON.stringify({
        account: { id: 2, username: 'emp001@example.com', role: 'EMPLOYEE' },
        employee: { id: 7, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', email: 'emp001@example.com', phone: null, status: 'ACTIVE' },
      }), { headers: { 'Content-Type': 'application/json' } }))
    }))

    const { authService } = await import('@/services/authService')
    const user = await authService.login({ email: 'emp001@example.com', password: 'Employee@123' }, false)

    expect(user.name).toBe('Nguyễn Văn An')
    expect(user.id).toBe('7')
  })
})

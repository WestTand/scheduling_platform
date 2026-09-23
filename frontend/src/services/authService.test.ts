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

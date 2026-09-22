import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { backendSwapRequestService } from '@/services/backendSwapRequestService'
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

const jsonResponse = (body: unknown, init: ResponseInit = {}) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' }, ...init })

describe('backendSwapRequestService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(jsonResponse([]))))
  })

  afterEach(() => vi.unstubAllGlobals())

  test('creates an employee swap request with assignment ids', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      id: 1,
      requesterAssignmentId: 100,
      requesterWorkDate: '2026-09-24',
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'B' },
      reason: 'Need swap',
      status: 'PENDING_TARGET',
      reviewedByAccountId: null,
      reviewedByUsername: null,
      reviewedAt: null,
    }))

    await backendSwapRequestService.createForEmployee({ requesterAssignmentId: 100, targetEmployeeId: 9, reason: 'Need swap' })

    const request = fetchMock.mock.calls[0]?.[1]
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/employee/swap-requests')
    expect(request).toEqual(expect.objectContaining({ method: 'POST' }))
    expect(request?.body).toBe(JSON.stringify({ requesterAssignmentId: 100, targetEmployeeId: 9, targetAssignmentId: null, reason: 'Need swap' }))
  })

  test('calls manager review endpoints', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse({
      id: 1,
      requesterAssignmentId: 100,
      requesterWorkDate: '2026-09-24',
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'B' },
      reason: null,
      status: 'APPROVED',
      reviewedByAccountId: 1,
      reviewedByUsername: 'manager',
      reviewedAt: '2026-09-22T10:00:00',
    })))

    await backendSwapRequestService.updateStatus('1', 'APPROVED')
    await backendSwapRequestService.updateStatus('1', 'REJECTED')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/swap-requests/1/approve')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/admin/swap-requests/1/reject')
  })
})

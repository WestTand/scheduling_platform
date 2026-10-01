import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { backendSwapRequestService } from '@/services/backendSwapRequestService'
import type { SwapRequest } from '@/types/management'

vi.mock('@/services/backendSwapRequestService', () => ({
  backendSwapRequestService: {
    respondForEmployee: vi.fn(),
  },
}))

const respondedRequest: SwapRequest = {
  id: '5',
  employeeName: 'A',
  employeeEmail: '',
  requestedDate: '2026-09-24',
  reason: '',
  status: 'PENDING_APPROVAL',
  submittedAt: '2026-09-24',
}

describe('swapRequestService', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('window', globalThis)
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.mocked(backendSwapRequestService.respondForEmployee).mockReset()
    vi.mocked(backendSwapRequestService.respondForEmployee).mockResolvedValue({ ...respondedRequest })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  test('forwards the chosen target assignment to the backend service', async () => {
    const { swapRequestService } = await import('@/services/swapRequestService')

    await swapRequestService.respondMine('5', true, 42)

    expect(backendSwapRequestService.respondForEmployee).toHaveBeenCalledWith('5', true, 42)
  })

  test('forwards an undefined target assignment when rejecting', async () => {
    const { swapRequestService } = await import('@/services/swapRequestService')

    await swapRequestService.respondMine('5', false)

    expect(backendSwapRequestService.respondForEmployee).toHaveBeenCalledWith('5', false, undefined)
  })

  test('mock mode shift options resolve to a numeric id the accept path can submit', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    const { swapRequestService } = await import('@/services/swapRequestService')
    const { resolveTargetAssignmentId } = await import('@/pages/employee/EmployeeSwapRequestsPage.helpers')

    const options = await swapRequestService.listMyAssignments()
    const selectedId = options[0].id
    const targetAssignmentId = resolveTargetAssignmentId(options, selectedId)

    expect(targetAssignmentId).toBe(Number(selectedId))

    const created = await swapRequestService.createMine({ requesterAssignmentId: '100', targetEmployeeId: '9', reason: 'Need swap' })
    const result = await swapRequestService.respondMine(created.id, true, targetAssignmentId ?? undefined)

    expect(backendSwapRequestService.respondForEmployee).not.toHaveBeenCalled()
    expect(result.status).toBe('PENDING_APPROVAL')
    expect(result.targetAssignmentId).toBe(String(selectedId))
  })

  test('mock mode records the chosen target assignment on the request', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    const { swapRequestService } = await import('@/services/swapRequestService')

    const created = await swapRequestService.createMine({ requesterAssignmentId: '100', targetEmployeeId: '9', reason: 'Need swap' })
    const result = await swapRequestService.respondMine(created.id, true, 42)

    expect(backendSwapRequestService.respondForEmployee).not.toHaveBeenCalled()
    expect(result.status).toBe('PENDING_APPROVAL')
    expect(result.targetAssignmentId).toBe('42')
  })

  test('mock mode leaves the target assignment empty when rejecting', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    const { swapRequestService } = await import('@/services/swapRequestService')

    const created = await swapRequestService.createMine({ requesterAssignmentId: '100', targetEmployeeId: '9', reason: 'Need swap' })
    const result = await swapRequestService.respondMine(created.id, false)

    expect(backendSwapRequestService.respondForEmployee).not.toHaveBeenCalled()
    expect(result.status).toBe('REJECTED')
    expect(result.targetAssignmentId).toBeUndefined()
  })
})

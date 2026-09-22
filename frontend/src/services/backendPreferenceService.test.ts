import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendPreferenceService } from '@/services/backendPreferenceService'
import { requestJson } from '@/services/apiClient'
import type { BackendPreferenceResponse } from '@/types/backendPreference'

vi.mock('@/services/apiClient', () => ({
  requestJson: vi.fn(),
}))

const mockResponse: BackendPreferenceResponse = {
  id: 12,
  employeeId: 100,
  employeeCode: 'NV001',
  employeeName: 'Nhan vien 1',
  preferenceDate: '2026-09-14',
  preferenceType: 'WANT',
  shiftTypeId: 10,
  shiftName: 'Ca sang',
  shiftCode: 'THONG_TAM',
  priority: 1,
  note: 'note',
  status: 'PENDING',
}

describe('backendPreferenceService', () => {
  beforeEach(() => {
    vi.mocked(requestJson).mockReset()
  })

  test('lists manager preferences with status query', async () => {
    vi.mocked(requestJson).mockResolvedValueOnce([mockResponse])

    const result = await backendPreferenceService.listForManager({ status: 'PENDING' })

    expect(requestJson).toHaveBeenCalledWith('/api/admin/preferences?status=PENDING')
    expect(result[0]).toMatchObject({ employeeName: 'Nhan vien 1', relatedShiftCode: 'THONG_TAM' })
  })

  test('approves a manager preference', async () => {
    vi.mocked(requestJson).mockResolvedValueOnce({ ...mockResponse, status: 'APPROVED' })

    await backendPreferenceService.approveForManager('12')

    expect(requestJson).toHaveBeenCalledWith('/api/admin/preferences/12/approve', { method: 'POST' })
  })

  test('creates an employee preference with body', async () => {
    vi.mocked(requestJson).mockResolvedValueOnce(mockResponse)

    await backendPreferenceService.createForEmployee({
      preferenceDate: '2026-09-14',
      preferenceType: 'PREFERRED',
      shiftTypeId: 10,
      shiftCode: 'THONG_TAM',
      priority: 1,
      note: 'note',
    })

    expect(requestJson).toHaveBeenCalledWith('/api/employee/preferences', {
      method: 'POST',
      body: {
        preferenceDate: '2026-09-14',
        preferenceType: 'WANT',
        shiftTypeId: 10,
        shiftCode: 'THONG_TAM',
        priority: 1,
        note: 'note',
      },
    })
  })

  test('cancels an employee preference', async () => {
    vi.mocked(requestJson).mockResolvedValueOnce(undefined)

    await backendPreferenceService.cancelForEmployee('12')

    expect(requestJson).toHaveBeenCalledWith('/api/employee/preferences/12/cancel', { method: 'POST' })
  })
})

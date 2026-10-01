import { describe, expect, test } from 'vitest'

import { adaptBackendPreference, toBackendPreferenceType } from '@/adapters/preferenceAdapter'
import type { BackendPreferenceResponse } from '@/types/backendPreference'

describe('preferenceAdapter', () => {
  test('maps WANT response to PREFERRED work preference', () => {
    const response: BackendPreferenceResponse = {
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
      note: 'Muon lam ca sang',
      status: 'PENDING',
    }

    expect(adaptBackendPreference(response)).toMatchObject({
      id: '12',
      employeeName: 'Nhan vien 1',
      relatedShiftCode: 'THONG_TAM',
      preferenceDate: '2026-09-14',
      type: 'PREFERRED',
      targetLabel: 'Ca sang',
      priority: 1,
      note: 'Muon lam ca sang',
      status: 'PENDING',
      submittedAt: '',
    })
  })

  test('maps AVOID response to AVOID work preference', () => {
    const response: BackendPreferenceResponse = {
      id: 13,
      employeeId: null,
      employeeCode: null,
      employeeName: null,
      preferenceDate: '2026-09-16',
      preferenceType: 'AVOID',
      shiftTypeId: null,
      shiftName: null,
      shiftCode: null,
      priority: 2,
      note: null,
      status: 'APPROVED',
    }

    const adapted = adaptBackendPreference(response)

    expect(adapted.type).toBe('AVOID')
    expect(adapted.employeeName).toBe('')
    expect(adapted.relatedShiftCode).toBeUndefined()
    expect(adapted.preferenceDate).toBe('2026-09-16')
    expect(adapted.submittedAt).toBe('')
    expect(adapted.targetLabel).toBe('Không có ca cụ thể')
    expect(adapted.note).toBe('')
  })

  test('maps frontend preference types to backend types', () => {
    expect(toBackendPreferenceType('PREFERRED')).toBe('WANT')
    expect(toBackendPreferenceType('PREFER_SHIFT')).toBe('WANT')
    expect(toBackendPreferenceType('AVOID')).toBe('AVOID')
    expect(toBackendPreferenceType('AVOID_SHIFT')).toBe('AVOID')
  })
})

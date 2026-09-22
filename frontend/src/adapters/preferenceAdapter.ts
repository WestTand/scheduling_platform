import type { BackendPreferenceResponse, BackendPreferenceType } from '@/types/backendPreference'
import type { PreferenceType, WorkPreference } from '@/types/management'
import type { ShiftCode } from '@/configs/shiftDefinitions'

export const toBackendPreferenceType = (type: PreferenceType): BackendPreferenceType => {
  if (type === 'AVOID' || type === 'AVOID_SHIFT') {
    return 'AVOID'
  }
  return 'WANT'
}

export const adaptBackendPreference = (response: BackendPreferenceResponse): WorkPreference => ({
  id: String(response.id),
  employeeName: response.employeeName ?? '',
  employeeEmail: '',
  month: response.preferenceDate.slice(0, 7),
  type: response.preferenceType === 'AVOID' ? 'AVOID' : 'PREFERRED',
  relatedShiftCode: response.shiftCode ? (response.shiftCode as ShiftCode) : undefined,
  note: response.note ?? '',
  priority: response.priority,
  submittedAt: response.preferenceDate,
  status: response.status,
  targetLabel: response.shiftName ?? 'Không có ca cụ thể',
})

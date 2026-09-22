export type BackendPreferenceType = 'WANT' | 'AVOID'

export type BackendPreferenceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendPreferenceResponse {
  id: number
  employeeId: number | null
  employeeCode: string | null
  employeeName: string | null
  preferenceDate: string
  preferenceType: BackendPreferenceType
  shiftTypeId: number | null
  shiftName: string | null
  shiftCode: string | null
  priority: number
  note: string | null
  status: BackendPreferenceStatus
}

export interface BackendCreatePreferenceRequest {
  preferenceDate: string
  preferenceType: BackendPreferenceType
  shiftTypeId?: number | null
  priority: number
  note?: string
}

export interface BackendUpdatePreferenceRequest {
  preferenceDate: string
  preferenceType: BackendPreferenceType
  shiftTypeId?: number | null
  priority: number
  note?: string
}

import type { ShiftTypeStatus } from '@/types/management'

export type BackendQuotaCategory = 'BATHING' | 'OVERTIME' | 'ON_CALL' | 'REGULAR'

export interface BackendShiftTypeResponse {
  id: number
  shiftName: string
  startTime: string
  endTime: string
  durationHours: number
  overnight: boolean
  heavy: boolean
  minStaff: number
  maxStaff: number
  quotaCategory: BackendQuotaCategory
  status: ShiftTypeStatus
  shiftCode: string
  applicableWeekdays: string
  appliesOnHolidays: boolean
  penaltyWeightMultiplier: number
}

export interface BackendCreateShiftTypeRequest {
  shiftName: string
  startTime: string
  endTime: string
  durationHours: number
  overnight: boolean
  heavy: boolean
  minStaff: number
  maxStaff: number
  quotaCategory: BackendQuotaCategory
  shiftCode: string
  applicableWeekdays: string
  appliesOnHolidays: boolean
  penaltyWeightMultiplier: number
}

export interface BackendUpdateShiftTypeRequest extends BackendCreateShiftTypeRequest {
  status: ShiftTypeStatus
}

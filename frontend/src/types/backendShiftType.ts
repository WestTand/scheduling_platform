import type { ShiftTypeStatus } from '@/types/management'

export type BackendQuotaCategory = 'REGULAR' | 'OVERTIME' | 'BATHING' | 'ON_CALL'

export interface BackendShiftTypeResponse {
  id: number
  shiftName: string
  startTime: string
  endTime: string
  durationHours: number | string
  overnight: boolean
  heavy: boolean
  minStaff: number
  maxStaff: number
  quotaCategory: BackendQuotaCategory
  status: ShiftTypeStatus
  shiftCode: string
  applicableWeekdays: string
  appliesOnHolidays: boolean
  penaltyWeightMultiplier: number | string
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

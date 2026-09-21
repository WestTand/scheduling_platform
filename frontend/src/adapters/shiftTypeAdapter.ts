import type { ShiftType, ShiftTypeFormValues } from '@/types/management'
import type {
  BackendCreateShiftTypeRequest,
  BackendQuotaCategory,
  BackendShiftTypeResponse,
  BackendUpdateShiftTypeRequest,
} from '@/types/backendShiftType'

type ShiftGroup = ShiftType['group']
type DayType = ShiftType['applicableDayType']

export function quotaToGroup(quota: BackendQuotaCategory): ShiftGroup {
  switch (quota) {
    case 'BATHING':
      return 'THONG_TAM'
    case 'OVERTIME':
      return 'NGOAI_GIO'
    case 'ON_CALL':
      return 'CA_TRUC'
    default:
      return 'THONG_TAM'
  }
}

export function groupToQuota(group: ShiftGroup): BackendQuotaCategory {
  switch (group) {
    case 'THONG_TAM':
      return 'BATHING'
    case 'NGOAI_GIO':
      return 'OVERTIME'
    case 'CA_TRUC':
      return 'ON_CALL'
    default:
      return 'BATHING'
  }
}

export function weekdaysForDayType(dayType: DayType): string {
  if (dayType === 'WEEKDAY') {
    return 'MON,TUE,WED,THU,FRI'
  }
  return 'SAT,SUN'
}

export function dayTypeFromBackend(applicableWeekdays: string, appliesOnHolidays: boolean): DayType {
  if (appliesOnHolidays) {
    return 'WEEKEND_OR_HOLIDAY'
  }
  const weekdays = (applicableWeekdays ?? '').toUpperCase()
  if (weekdays.includes('SAT') || weekdays.includes('SUN')) {
    return 'WEEKEND_OR_HOLIDAY'
  }
  return 'WEEKDAY'
}

export function durationHoursToMinutes(durationHours: number | string): number {
  return Math.round(Number(durationHours) * 60)
}

export function workMinutesToDurationHours(workMinutes: number): number {
  return Number((workMinutes / 60).toFixed(1))
}

export function adaptBackendShiftType(backend: BackendShiftTypeResponse): ShiftType {
  return {
    id: String(backend.id),
    code: backend.shiftCode,
    name: backend.shiftName,
    group: quotaToGroup(backend.quotaCategory),
    startTime: backend.startTime.slice(0, 5),
    endTime: backend.endTime.slice(0, 5),
    crossesMidnight: backend.overnight,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: durationHoursToMinutes(backend.durationHours),
    applicableDayType: dayTypeFromBackend(backend.applicableWeekdays, backend.appliesOnHolidays),
    color: backend.heavy ? '#ef4444' : '#2563eb',
    description: `Min ${backend.minStaff}, max ${backend.maxStaff}`,
    status: backend.status,
  }
}

export function toBackendCreateShiftTypeRequest(form: ShiftTypeFormValues): BackendCreateShiftTypeRequest {
  return {
    shiftName: form.name.trim(),
    startTime: form.startTime,
    endTime: form.endTime,
    durationHours: workMinutesToDurationHours(form.workMinutes),
    overnight: form.crossesMidnight,
    heavy: form.group === 'THONG_TAM',
    minStaff: 1,
    maxStaff: 20,
    quotaCategory: groupToQuota(form.group),
    shiftCode: form.code.trim(),
    applicableWeekdays: weekdaysForDayType(form.applicableDayType),
    appliesOnHolidays: form.applicableDayType === 'WEEKEND_OR_HOLIDAY',
    penaltyWeightMultiplier: form.group === 'NGOAI_GIO' ? 1.2 : 1,
  }
}

export function toBackendUpdateShiftTypeRequest(form: ShiftTypeFormValues): BackendUpdateShiftTypeRequest {
  return {
    ...toBackendCreateShiftTypeRequest(form),
    status: form.status,
  }
}

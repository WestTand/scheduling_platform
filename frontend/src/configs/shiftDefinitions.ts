export type ShiftCode =
  | 'THONG_TAM'
  | 'NGOAI_GIO_1'
  | 'NGOAI_GIO_2'
  | 'TRUC_SANG'
  | 'TRUC_CHIEU'
  | 'TRUC_DEM'

export type ShiftGroup = 'THONG_TAM' | 'NGOAI_GIO' | 'CA_TRUC'

export type ApplicableDayType = 'WEEKDAY' | 'WEEKEND_OR_HOLIDAY'

export interface ShiftDefinition {
  code: ShiftCode
  name: string
  group: ShiftGroup
  startTime: string
  endTime: string
  crossesMidnight: boolean
  breakStart?: string
  breakEnd?: string
  breakMinutes: number
  workMinutes: number
  applicableDayType: ApplicableDayType
  active: boolean
  color: string
  description: string
}

export const shiftDefinitions: ShiftDefinition[] = [
  {
    code: 'THONG_TAM',
    name: 'Ca thông tầm',
    group: 'THONG_TAM',
    startTime: '08:00',
    endTime: '17:00',
    crossesMidnight: false,
    breakStart: '12:00',
    breakEnd: '13:00',
    breakMinutes: 60,
    workMinutes: 480,
    applicableDayType: 'WEEKDAY',
    active: true,
    color: '#2563eb',
    description: 'Ca thông tầm áp dụng từ Thứ Hai đến Thứ Sáu.',
  },
  {
    code: 'NGOAI_GIO_1',
    name: 'Ca ngoài giờ 1',
    group: 'NGOAI_GIO',
    startTime: '17:00',
    endTime: '00:30',
    crossesMidnight: true,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: 450,
    applicableDayType: 'WEEKDAY',
    active: true,
    color: '#f97316',
    description: 'Ca ngoài giờ 1 kéo dài qua nửa đêm.',
  },
  {
    code: 'NGOAI_GIO_2',
    name: 'Ca ngoài giờ 2',
    group: 'NGOAI_GIO',
    startTime: '00:30',
    endTime: '08:00',
    crossesMidnight: false,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: 450,
    applicableDayType: 'WEEKDAY',
    active: true,
    color: '#7c3aed',
    description: 'Ca ngoài giờ 2 đối xứng với ca ngoài giờ 1.',
  },
  {
    code: 'TRUC_SANG',
    name: 'Trực sáng',
    group: 'CA_TRUC',
    startTime: '08:00',
    endTime: '16:00',
    crossesMidnight: false,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: 480,
    applicableDayType: 'WEEKEND_OR_HOLIDAY',
    active: true,
    color: '#16a34a',
    description: 'Ca trực sáng áp dụng cho Thứ Bảy, Chủ nhật và ngày lễ.',
  },
  {
    code: 'TRUC_CHIEU',
    name: 'Trực chiều',
    group: 'CA_TRUC',
    startTime: '16:00',
    endTime: '00:00',
    crossesMidnight: true,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: 480,
    applicableDayType: 'WEEKEND_OR_HOLIDAY',
    active: true,
    color: '#d97706',
    description: 'Ca trực chiều kết thúc vào ngày hôm sau.',
  },
  {
    code: 'TRUC_DEM',
    name: 'Trực đêm',
    group: 'CA_TRUC',
    startTime: '00:00',
    endTime: '08:00',
    crossesMidnight: false,
    breakStart: undefined,
    breakEnd: undefined,
    breakMinutes: 0,
    workMinutes: 480,
    applicableDayType: 'WEEKEND_OR_HOLIDAY',
    active: true,
    color: '#0f766e',
    description: 'Ca trực đêm áp dụng cho Thứ Bảy, Chủ nhật và ngày lễ.',
  },
]

export const shiftDefinitionMap = Object.fromEntries(
  shiftDefinitions.map((shift) => [shift.code, shift]),
) as Record<ShiftCode, ShiftDefinition>

const dutyShiftCodes: ShiftCode[] = ['TRUC_SANG', 'TRUC_CHIEU', 'TRUC_DEM']
export const dutyShifts = dutyShiftCodes.map((code) => shiftDefinitionMap[code])
export const weekdayShifts = shiftDefinitions.filter((shift) => shift.applicableDayType === 'WEEKDAY')
export const weekendOrHolidayShifts = shiftDefinitions.filter((shift) => shift.applicableDayType === 'WEEKEND_OR_HOLIDAY')

export function getShiftDefinition(code: string) {
  return shiftDefinitionMap[code as ShiftCode]
}

export function getShiftCodeForDate(date: string, weekdayCode: ShiftCode, weekendCode: ShiftCode) {
  const day = new Date(`${date}T00:00:00`).getDay()
  return day === 0 || day === 6 ? weekendCode : weekdayCode
}

export function getShiftDefinitionsForDate(date: string) {
  if (!date) {
    return shiftDefinitions
  }

  const day = new Date(`${date}T00:00:00`).getDay()
  return day === 0 || day === 6 ? weekendOrHolidayShifts : weekdayShifts
}

export function formatShiftRangeLabel(shift: Pick<ShiftDefinition, 'startTime' | 'endTime' | 'crossesMidnight'>) {
  const suffix = shift.crossesMidnight ? ' · Kết thúc ngày hôm sau' : ''
  return `${shift.startTime}–${shift.endTime}${suffix}`
}

export function getWorkMinutes(startTime: string, endTime: string) {
  const [startHour, startMinute] = startTime.split(':').map(Number)
  const [endHour, endMinute] = endTime.split(':').map(Number)
  const startTotal = startHour * 60 + startMinute
  const endTotal = endHour * 60 + endMinute

  if (endTotal >= startTotal) {
    return endTotal - startTotal
  }

  return 24 * 60 - startTotal + endTotal
}

import type { BackendEmployeeScheduleItem } from '@/services/employeeScheduleService'
import type { ShiftCode } from '@/configs/shiftDefinitions'

export interface EmployeeScheduleViewItem {
  id: string
  date: string
  day: string
  shiftCode: ShiftCode
  shiftName: string
  timeRange: string
  status: string
  warning: string
}

const shiftNameToCode = (shiftName: string): ShiftCode => {
  const normalized = shiftName.trim().toLowerCase()
  if (normalized === 'thong tam') return 'THONG_TAM'
  if (normalized === 'ngoai gio 1') return 'NGOAI_GIO_1'
  if (normalized === 'ngoai gio 2') return 'NGOAI_GIO_2'
  if (normalized === 'truc sang') return 'TRUC_SANG'
  if (normalized === 'truc chieu') return 'TRUC_CHIEU'
  if (normalized === 'truc dem') return 'TRUC_DEM'
  return 'THONG_TAM'
}

export const getEmployeeWeekWindow = (weekOffset = 0, baseDate: Date = new Date()) => {
  const base = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate())
  const weekday = (base.getDay() + 6) % 7
  const monday = new Date(base)
  monday.setDate(base.getDate() - weekday + weekOffset * 7)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  const toInput = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return { from: toInput(monday), to: toInput(sunday) }
}

export const adaptBackendEmployeeSchedule = (items: BackendEmployeeScheduleItem[]): EmployeeScheduleViewItem[] => items.map((item) => ({
  id: String(item.assignmentId),
  date: item.workDate,
  day: new Date(`${item.workDate}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'long' }),
  shiftCode: shiftNameToCode(item.shiftTypeName ?? ''),
  shiftName: item.shiftTypeName,
  timeRange: `${item.shiftStartTime} - ${item.shiftEndTime}`,
  status: item.assignmentStatus ?? 'Đã xác nhận',
  warning: '',
}))

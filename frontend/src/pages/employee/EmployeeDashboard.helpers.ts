import type { EmployeeScheduleViewItem } from './EmployeeSchedulePage.helpers'

export interface EmployeeSummaryCard {
  label: string
  value: string
  detail: string
}

export const getEmployeeSummaryCards = ({
  todayShiftName,
  nextShiftName,
  totalMinutes,
  pendingLeave,
  pendingPreference,
  pendingSwap,
}: {
  todayShiftName: string | null
  nextShiftName: string | null
  totalMinutes: number
  pendingLeave: number
  pendingPreference: number
  pendingSwap: number
}): EmployeeSummaryCard[] => [
  { label: 'Ca làm hôm nay', value: todayShiftName ?? '—', detail: 'Hôm nay' },
  { label: 'Ca làm tiếp theo', value: nextShiftName ?? '—', detail: 'Sắp tới' },
  { label: 'Tổng giờ làm', value: `${Math.round(totalMinutes / 60)}h`, detail: 'Tuần này' },
  { label: 'Yêu cầu chờ xử lý', value: String(pendingLeave + pendingPreference + pendingSwap), detail: `${pendingSwap} đổi ca, ${pendingLeave} nghỉ phép` },
]

export const pickNextShiftDays = (items: EmployeeScheduleViewItem[], limit: number): EmployeeScheduleViewItem[] => {
  const sorted = [...items].sort((first, second) => first.date.localeCompare(second.date))
  const seen = new Set<string>()
  const days: EmployeeScheduleViewItem[] = []
  for (const item of sorted) {
    if (seen.has(item.date)) continue
    seen.add(item.date)
    days.push(item)
    if (days.length >= limit) break
  }
  return days
}

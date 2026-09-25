import { getShiftDefinition } from '@/configs/shiftDefinitions'
import type { ScheduleDetail } from '@/types/schedule'

export interface ManagerSummaryCard {
  label: string
  value: string
  context: string
  path: string
}

export interface ManagerActionItem {
  title: string
  related: string
  detail: string
  priority: 'Cao' | 'Trung bình'
  status: string
  path: string
}

export const getManagerSummaryCards = ({
  employeeTotal,
  weekShiftCount,
  pendingLeave,
  pendingSwap,
  pendingPreference,
  warningCount,
}: {
  employeeTotal: number
  weekShiftCount: number
  pendingLeave: number
  pendingSwap: number
  pendingPreference: number
  warningCount: number
}): ManagerSummaryCard[] => [
  { label: 'Tổng nhân viên', value: String(employeeTotal), context: 'đang hoạt động', path: '/manager/employees' },
  { label: 'Ca trực tuần này', value: String(weekShiftCount), context: 'tuần hiện tại', path: '/manager/schedules' },
  { label: 'Yêu cầu chờ duyệt', value: String(pendingLeave + pendingSwap + pendingPreference), context: 'nghỉ phép, đổi ca, nguyện vọng', path: '/manager/dashboard#action-center' },
  { label: 'Cảnh báo xung đột', value: String(warningCount), context: 'kỳ nháp tuần này', path: '/manager/schedules' },
]

export const getManagerActionItems = ({
  draftPeriod,
  leave,
  swap,
  preference,
  criticalWarning,
}: {
  draftPeriod: { id: string; name: string } | null
  leave: { employeeName: string; startDate: string; endDate: string } | null
  swap: { employeeName: string; reason: string } | null
  preference: { employeeName: string; note: string } | null
  criticalWarning: { periodId: string; employeeName: string; content: string } | null
}): ManagerActionItem[] => [
  {
    title: 'Lịch nháp chờ xử lý',
    related: draftPeriod?.name ?? 'Không có lịch nháp',
    detail: draftPeriod ? `Kỳ lịch ${draftPeriod.name}.` : '',
    priority: 'Cao',
    status: 'Chờ xử lý',
    path: '/manager/schedules?status=DRAFT',
  },
  {
    title: 'Ngày nghỉ chờ duyệt',
    related: leave?.employeeName ?? 'Không có yêu cầu',
    detail: leave ? `Yêu cầu từ ${leave.startDate} đến ${leave.endDate}.` : '',
    priority: 'Cao',
    status: 'Chờ duyệt',
    path: '/manager/leave-requests?status=PENDING',
  },
  {
    title: 'Đổi ca chờ xử lý',
    related: swap?.employeeName ?? 'Không có yêu cầu',
    detail: swap?.reason ?? '',
    priority: 'Trung bình',
    status: 'Chờ xử lý',
    path: '/manager/swap-requests?status=PENDING_APPROVAL',
  },
  {
    title: 'Nguyện vọng chưa xem',
    related: preference?.employeeName ?? 'Không có nguyện vọng',
    detail: preference?.note ?? '',
    priority: 'Trung bình',
    status: 'Chưa xem',
    path: '/manager/preferences?status=PENDING',
  },
  {
    title: 'Cảnh báo lịch nghiêm trọng',
    related: criticalWarning?.employeeName ?? 'Không có cảnh báo',
    detail: criticalWarning?.content ?? '',
    priority: 'Cao',
    status: 'Cần xem',
    path: criticalWarning ? `/manager/schedules/${criticalWarning.periodId}?tab=warnings` : '/manager/schedules',
  },
]

export interface UpcomingShiftRow {
  key: string
  periodId: string
  date: string
  dateLabel: string
  shiftCode: string
  shiftName: string
  assignedCount: number
  unassigned: boolean
  hasWarning: boolean
  path: string
}

export interface ScheduleAlertRow {
  id: string
  periodId: string
  title: string
  detail: string
  severity: 'Cao' | 'Trung bình' | 'Thấp'
  path: string
}

const formatDateLabel = (date: string) => {
  const weekday = new Date(`${date}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'long' })
  const [, month, day] = date.split('-')
  return `${weekday} - ${day}/${month}`
}

const alertSeverityLabel = (severity: string): ScheduleAlertRow['severity'] => {
  if (severity === 'HIGH') return 'Cao'
  if (severity === 'MEDIUM') return 'Trung bình'
  return 'Thấp'
}

const alertTitle = (type: string) => {
  if (type === 'HARD_CONSTRAINT') return 'Vi phạm ràng buộc cứng'
  if (type === 'SOFT_CONSTRAINT') return 'Cảnh báo mềm'
  return 'Thông tin lịch'
}

export const pickUpcomingShifts = (details: ScheduleDetail[], limit: number): UpcomingShiftRow[] => {
  const rows = details.flatMap((detail) => detail.assignments.map((assignment) => {
    const assignedCount = assignment.employeeNames.length
    const unassigned = assignedCount === 0
    const hasWarning = assignment.warningCount > 0
    return {
      key: `${detail.id}-${assignment.workShiftId ?? assignment.id}`,
      periodId: detail.id,
      date: assignment.date,
      dateLabel: formatDateLabel(assignment.date),
      shiftCode: assignment.shiftCode,
      shiftName: getShiftDefinition(assignment.shiftCode)?.name ?? assignment.shiftCode,
      assignedCount,
      unassigned,
      hasWarning,
      path: hasWarning || unassigned ? `/manager/schedules/${detail.id}?tab=warnings` : `/manager/schedules/${detail.id}`,
    } satisfies UpcomingShiftRow
  }))
  rows.sort((first, second) => first.date.localeCompare(second.date))
  return rows.slice(0, limit)
}

export const pickScheduleAlerts = (details: ScheduleDetail[], limit: number): ScheduleAlertRow[] => {
  const rank = (severity: string) => severity === 'HIGH' ? 0 : severity === 'MEDIUM' ? 1 : 2
  const alerts = details.flatMap((detail) => detail.warnings.map((warning) => ({
    id: warning.id,
    periodId: detail.id,
    title: alertTitle(warning.type),
    detail: warning.content,
    severity: alertSeverityLabel(warning.severity),
    path: `/manager/schedules/${detail.id}?tab=warnings`,
  } satisfies ScheduleAlertRow)))
  alerts.sort((first, second) => rank(first.severity === 'Cao' ? 'HIGH' : first.severity === 'Trung bình' ? 'MEDIUM' : 'LOW') - rank(second.severity === 'Cao' ? 'HIGH' : second.severity === 'Trung bình' ? 'MEDIUM' : 'LOW'))
  return alerts.slice(0, limit)
}

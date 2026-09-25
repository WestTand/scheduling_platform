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

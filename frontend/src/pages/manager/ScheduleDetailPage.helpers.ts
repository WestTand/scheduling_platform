import type { ScheduleStaffingAdvice, ScheduleStatus } from '@/types/schedule'

export type ScheduleDetailAction = 'approve' | 'publish' | 'cancel'
export type ScheduleDetailNotice = { type: 'success' | 'error'; message: string }

export const INFEASIBLE_APPROVE_MESSAGE =
  'Lịch đang bị cảnh báo (INFE) nên không thể duyệt. Hãy chạy lại AI Optimize hoặc chỉnh lịch trước khi duyệt.'

export const getScheduleDetailActions = (status: ScheduleStatus): ScheduleDetailAction[] => {
  if (status === 'DRAFT') return ['approve', 'cancel']
  if (status === 'APPROVED') return ['publish', 'cancel']
  return []
}

export const canApproveSchedule = (status: ScheduleStatus, optimizationStatus?: string | null) =>
  getScheduleDetailActions(status).includes('approve') && optimizationStatus !== 'INFEASIBLE'

export const getScheduleDetailReadOnlyMessage = (status: ScheduleStatus, optimizationStatus?: string | null) => {
  if (optimizationStatus === 'INFEASIBLE') return INFEASIBLE_APPROVE_MESSAGE
  if (status === 'PUBLISHED') return 'Lịch đã công bố nên trang chi tiết chỉ cho phép xem.'
  if (status === 'CANCELLED') return 'Lịch đã hủy nên trang chi tiết chỉ cho phép xem.'
  return ''
}

export const getScheduleDetailNoticeClass = (type: ScheduleDetailNotice['type']) => type === 'error'
  ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'

const staffingReasonLabels: Record<string, string> = {
  WEEKLY_CAPACITY: 'giới hạn số ca mỗi tuần',
  QUOTA_OVERTIME: 'hạn mức ca ngoài giờ mỗi tuần',
  QUOTA_BATHING: 'hạn mức ca thông tầm mỗi tuần',
  ON_CALL_GAP: 'khoảng cách giữa các ngày trực',
  SAME_DAY_REST: 'thời gian nghỉ tối thiểu giữa các ca',
}

const UNREACHABLE_STAFFING_MESSAGE =
  'Không có số lượng nhân viên nào xếp được lịch này: có quy tắc khiến lịch không thể thực hiện, nên tăng pool lên bao nhiêu người cũng không giải quyết được.'

export const getScheduleDetailStaffingMessage = (optimizationStatus?: string | null, advice?: ScheduleStaffingAdvice | null) => {
  if (optimizationStatus !== 'INFEASIBLE' || !advice) return ''

  const reasonLabel = advice.reasons?.map((reason) => staffingReasonLabels[reason]).find((label) => Boolean(label))
  const reasonClause = reasonLabel ? ` Lý do chính: ${reasonLabel}.` : ''

  if (advice.unreachable) return `${UNREACHABLE_STAFFING_MESSAGE}${reasonClause}`

  const shortage = advice.exact
    ? `Lịch này cần tối thiểu ${advice.requiredEmployees} nhân viên nhưng pool hiện chỉ có ${advice.availableEmployees}.`
    : `Lịch này ước tính cần ít nhất ${advice.requiredEmployees} nhân viên (AI chưa chứng minh được con số chính xác) nhưng pool hiện chỉ có ${advice.availableEmployees}.`

  return `${shortage}${reasonClause}`
}

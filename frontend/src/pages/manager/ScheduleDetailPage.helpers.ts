import type { ScheduleStatus } from '@/types/schedule'

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

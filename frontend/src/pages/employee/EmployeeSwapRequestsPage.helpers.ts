import type { SwapAssignmentOption } from '@/types/management'

export const SWAP_CONFIRM_BLOCKED_MESSAGE = 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.'

export const formatSwapAssignmentOptionLabel = (assignment: SwapAssignmentOption) =>
  `${assignment.workDate} • ${assignment.shiftName} ${assignment.shiftStartTime}-${assignment.shiftEndTime} • ${assignment.periodName} (${assignment.periodStatus})`

export const resolvePublishedAssignmentId = (options: SwapAssignmentOption[], selectedId: string) => {
  const normalized = selectedId.trim()

  if (!normalized) {
    return null
  }

  const match = options.find((option) => option.id === normalized && option.periodStatus === 'PUBLISHED')

  return match ? match.id : null
}

export const getRespondBlockedMessage = (options: SwapAssignmentOption[], selectedId: string, accept: boolean, loading = false) => {
  if (!accept) {
    return null
  }

  if (loading) {
    return 'Đang tải ca làm của bạn, vui lòng đợi.'
  }

  return resolvePublishedAssignmentId(options, selectedId) ? null : SWAP_CONFIRM_BLOCKED_MESSAGE
}

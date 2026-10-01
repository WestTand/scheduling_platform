import type { SwapAssignmentOption } from '@/types/management'

export const SWAP_CONFIRM_BLOCKED_MESSAGE = 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.'
export const SWAP_LOADING_BLOCKED_MESSAGE = 'Đang tải ca làm của bạn, vui lòng đợi.'
export const SWAP_NO_PUBLISHED_SHIFT_MESSAGE = 'Bạn không có ca nào đã xuất bản để đổi.'

export const formatSwapAssignmentOptionLabel = (assignment: SwapAssignmentOption) =>
  `${assignment.workDate} • ${assignment.shiftName} ${assignment.shiftStartTime}-${assignment.shiftEndTime} • ${assignment.periodName} (${assignment.periodStatus})`

export const getPublishedSwapAssignmentOptions = (options: SwapAssignmentOption[]) =>
  options.filter((option) => option.periodStatus === 'PUBLISHED')

export const resolveTargetAssignmentId = (options: SwapAssignmentOption[], selectedId: string): number | null => {
  const normalized = selectedId.trim()

  if (!normalized) {
    return null
  }

  const match = getPublishedSwapAssignmentOptions(options).find((option) => option.id === normalized)

  if (!match) {
    return null
  }

  const numericId = Number(normalized)

  return Number.isInteger(numericId) && numericId > 0 ? numericId : null
}

export interface RespondDialogState {
  options: SwapAssignmentOption[]
  selectedId: string
  loading: boolean
  error: string
}

export type RespondConfirmation =
  | { allowed: false; variant: 'warning' | 'error'; message: string }
  | { allowed: true; targetAssignmentId: number | undefined }

export const getRespondConfirmation = (accept: boolean, dialog: RespondDialogState | null): RespondConfirmation => {
  if (!accept) {
    return { allowed: true, targetAssignmentId: undefined }
  }

  if (!dialog) {
    return { allowed: false, variant: 'warning', message: SWAP_CONFIRM_BLOCKED_MESSAGE }
  }

  if (dialog.error) {
    return { allowed: false, variant: 'error', message: dialog.error }
  }

  if (dialog.loading) {
    return { allowed: false, variant: 'warning', message: SWAP_LOADING_BLOCKED_MESSAGE }
  }

  const targetAssignmentId = resolveTargetAssignmentId(dialog.options, dialog.selectedId)

  if (targetAssignmentId !== null) {
    return { allowed: true, targetAssignmentId }
  }

  if (getPublishedSwapAssignmentOptions(dialog.options).length === 0) {
    return { allowed: false, variant: 'warning', message: SWAP_NO_PUBLISHED_SHIFT_MESSAGE }
  }

  return { allowed: false, variant: 'warning', message: SWAP_CONFIRM_BLOCKED_MESSAGE }
}
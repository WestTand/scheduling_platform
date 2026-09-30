import type { ScheduleAssignment, ScheduleDetail } from '@/types/schedule'

export type ScheduleEditNotice = { type: 'success' | 'error'; message: string }

export type ScheduleEditAssignmentFilter = 'ALL' | 'UNASSIGNED' | 'WARNING'

export type ScheduleEditRowIntent =
  | { mode: 'assign'; workShiftId: string; assignmentId: null }
  | { mode: 'update'; workShiftId: string; assignmentId: string }

/**
 * Unassigned rows are synthesized by the adapter with a `work-shift-<id>` placeholder id,
 * so they must be created through the work-shift endpoint instead of the assignment one.
 */
export const getScheduleEditRowIntent = (assignment: ScheduleAssignment): ScheduleEditRowIntent => {
  const workShiftId = assignment.workShiftId ?? ''
  return assignment.employeeNames.length > 0
    ? { mode: 'update', workShiftId, assignmentId: assignment.id }
    : { mode: 'assign', workShiftId, assignmentId: null }
}

export const getScheduleEditDeleteDisabled = (assignment: ScheduleAssignment) =>
  getScheduleEditRowIntent(assignment).mode === 'assign'

export const canSubmitEditRequest = (useMockApi: boolean) => useMockApi

export const getScheduleEditDialogDescription = (useMockApi: boolean, isAssign: boolean) => {
  if (useMockApi) return 'Chi tiết dưới đây chỉ cập nhật mock state để kiểm thử giao diện.'
  return isAssign ? 'Chọn nhân viên để gán vào ca đã chọn.' : 'Cập nhật nhân viên được phân công cho ca này.'
}

export const getScheduleEditModeLabel = (useMockApi: boolean) => useMockApi ? 'Chế độ chỉnh sửa mock' : 'Backend mode: chỉnh sửa phân công trực tiếp'

export const shouldShowMockStatusActions = (useMockApi: boolean) => useMockApi

export const canRunScheduleEditOptimize = ({
  useMockApi,
  scheduleId,
  isEditingLocked,
  isOptimizing,
}: {
  useMockApi: boolean
  scheduleId: string
  isEditingLocked: boolean
  isOptimizing: boolean
}) => !useMockApi && Boolean(scheduleId) && !isEditingLocked && !isOptimizing

export const getScheduleEditLockedMessage = (useMockApi: boolean, status?: ScheduleDetail['status']) => {
  if (useMockApi) return ''
  if (status === 'PUBLISHED') return 'Lịch đã công bố nên không thể chỉnh sửa phân công.'
  if (status === 'CANCELLED') return 'Lịch đã hủy nên không thể chỉnh sửa phân công.'
  return ''
}

export const getScheduleEditNoticeClass = (type: ScheduleEditNotice['type']) => type === 'error'
  ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'

export const filterScheduleEditAssignments = (assignments: ScheduleAssignment[], filter: ScheduleEditAssignmentFilter) => {
  if (filter === 'UNASSIGNED') return assignments.filter((assignment) => assignment.employeeNames.length === 0)
  if (filter === 'WARNING') return assignments.filter((assignment) => assignment.warningCount > 0)
  return assignments
}

export const reloadScheduleEditDetail = async ({
  scheduleId,
  loadDetail,
  setBackendResult,
  setAssignments,
}: {
  scheduleId: string
  loadDetail: (scheduleId: string) => Promise<ScheduleDetail>
  setBackendResult: (result: { scheduleId: string; detail: ScheduleDetail; error: string }) => void
  setAssignments: (assignments: ScheduleAssignment[]) => void
}) => {
  const refreshed = await loadDetail(scheduleId)
  setBackendResult({ scheduleId, detail: refreshed, error: '' })
  setAssignments(refreshed.assignments)
  return refreshed
}

import type { ScheduleStaffingAdvice, ScheduleStatus } from '@/types/schedule'
import type { Employee } from '@/types/management'
import type { PoolCandidate } from '@/components/schedule/ScheduleEmployeePoolEditor.helpers'

export type ScheduleDetailAction = 'approve' | 'publish' | 'cancel'
export type ScheduleDetailNotice = { type: 'success' | 'error'; message: string }

export const INFEASIBLE_APPROVE_MESSAGE =
  'Lịch đang bị cảnh báo (INFE) nên không thể duyệt. Hãy chạy lại AI Optimize hoặc chỉnh lịch trước khi duyệt.'

export const isInfeasibleOptimizationStatus = (optimizationStatus?: string | null) =>
  optimizationStatus === 'INFEASIBLE' || optimizationStatus === 'INFE'

export const getScheduleDetailActions = (status: ScheduleStatus): ScheduleDetailAction[] => {
  if (status === 'DRAFT') return ['approve', 'cancel']
  if (status === 'APPROVED') return ['publish', 'cancel']
  return []
}

export const canApproveSchedule = (status: ScheduleStatus, optimizationStatus?: string | null) =>
  getScheduleDetailActions(status).includes('approve') && !isInfeasibleOptimizationStatus(optimizationStatus)

export const getScheduleDetailReadOnlyMessage = (status: ScheduleStatus, optimizationStatus?: string | null) => {
  if (isInfeasibleOptimizationStatus(optimizationStatus)) return INFEASIBLE_APPROVE_MESSAGE
  if (status === 'PUBLISHED') return 'Lịch đã công bố nên trang chi tiết chỉ cho phép xem.'
  if (status === 'CANCELLED') return 'Lịch đã hủy nên trang chi tiết chỉ cho phép xem.'
  return ''
}

export const getScheduleDetailNoticeClass = (type: ScheduleDetailNotice['type']) => type === 'error'
  ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'

export const getPoolEditorCandidates = (employees: Employee[]): PoolCandidate[] => employees
  .filter((employee) => employee.aiSchedulable === true)
  .map((employee) => ({ id: employee.id, fullName: employee.fullName, employeeCode: employee.employeeCode }))

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
  if (!isInfeasibleOptimizationStatus(optimizationStatus) || !advice) return ''

  const reasonLabel = advice.reasons?.map((reason) => staffingReasonLabels[reason]).find((label) => Boolean(label))
  const reasonClause = reasonLabel ? ` Lý do chính: ${reasonLabel}.` : ''

  if (advice.unreachable) return `${UNREACHABLE_STAFFING_MESSAGE}${reasonClause}`

  if (advice.availableEmployees >= advice.requiredEmployees) {
    const threshold = advice.exact ? 'mức tối thiểu' : 'mức ước tính'
    return `Pool hiện có ${advice.availableEmployees} nhân viên, không thấp hơn ${threshold} ${advice.requiredEmployees} mà AI tính được. Lịch vẫn không xếp được, có thể do nghỉ phép, phân công khóa hoặc ràng buộc theo từng ca; hãy kiểm tra cảnh báo chi tiết.${reasonClause}`
  }

  const shortage = advice.exact
    ? `Lịch này cần tối thiểu ${advice.requiredEmployees} nhân viên nhưng pool hiện chỉ có ${advice.availableEmployees}.`
    : `Lịch này ước tính cần ít nhất ${advice.requiredEmployees} nhân viên (AI chưa chứng minh được con số chính xác) nhưng pool hiện chỉ có ${advice.availableEmployees}.`

  return `${shortage}${reasonClause}`
}

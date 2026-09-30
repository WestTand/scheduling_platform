export type ScheduleGenerationNoticeType = 'success' | 'warning' | 'error'

export const canOptimizeSchedulePeriod = ({ selectedPeriodId, isSubmitting }: { selectedPeriodId: string; isSubmitting: boolean }) => Boolean(selectedPeriodId) && !isSubmitting

export const canEditSelectedPeriodPool = ({ selectedPeriodId, isSubmitting }: { selectedPeriodId: string; isSubmitting: boolean }) => Boolean(selectedPeriodId) && !isSubmitting

const technicalOptimizeErrorPatterns = [
  'Cannot lazily initialize collection',
  'org.hibernate',
  'LazyInitializationException',
]

export const getOptimizeErrorMessage = (error: unknown) => {
  const message = error instanceof Error ? error.message : ''
  if (message && !technicalOptimizeErrorPatterns.some((pattern) => message.includes(pattern))) return message
  return 'Không thể tối ưu lịch. Vui lòng kiểm tra pool nhân viên hoặc thử lại.'
}

export const getScheduleGenerationNoticeClass = (type: ScheduleGenerationNoticeType) => {
  if (type === 'error') return 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
  if (type === 'warning') return 'rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800'
  return 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'
}

export const summarizeOptimizeResult = ({ aiStatus, solverStatus, assignmentCount }: { aiStatus: string; solverStatus: string | null; assignmentCount: number | null }) => [
  `AI: ${aiStatus}`,
  solverStatus ? `Solver: ${solverStatus}` : '',
  typeof assignmentCount === 'number' ? `${assignmentCount} phân công` : '',
].filter(Boolean).join(' · ')

export const buildGenerationNotice = (
  _periodName: string,
  result: { aiStatus: string; solverStatus: string | null; assignmentCount: number | null; errorMessage?: string | null },
) => {
  if (result.aiStatus === 'SUCCESS') {
    return { type: 'success' as const, message: 'Tạo lịch thành công.' }
  }
  if (result.aiStatus === 'INFEASIBLE') {
    return { type: 'warning' as const, message: 'Đã tạo lịch nhưng chưa phân công đủ. Vui lòng xem cảnh báo hoặc chỉnh sửa lịch.' }
  }
  return { type: 'error' as const, message: 'Không thể tạo lịch. Vui lòng kiểm tra dữ liệu và thử lại.' }
}

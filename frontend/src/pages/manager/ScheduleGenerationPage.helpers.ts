export type ScheduleGenerationNoticeType = 'success' | 'warning' | 'error'

export const canOptimizeSchedulePeriod = ({ selectedPeriodId, isSubmitting }: { selectedPeriodId: string; isSubmitting: boolean }) => Boolean(selectedPeriodId) && !isSubmitting

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
  periodName: string,
  result: { aiStatus: string; solverStatus: string | null; assignmentCount: number | null; errorMessage?: string | null },
) => {
  const summary = summarizeOptimizeResult(result)
  if (result.aiStatus === 'SUCCESS') {
    return { type: 'success' as const, message: `Đã sinh lịch ${periodName}. ${summary}` }
  }
  const reason = result.errorMessage ? ` Lý do: ${result.errorMessage}.` : ''
  if (result.aiStatus === 'INFEASIBLE') {
    return { type: 'warning' as const, message: `Đã tạo ca cho ${periodName} nhưng AI chưa phân công được. ${summary}.${reason} Xem tab Cảnh báo hoặc vào Chỉnh sửa để phân công thủ công.` }
  }
  return { type: 'error' as const, message: `Đã tạo ca cho ${periodName} nhưng AI tối ưu thất bại. ${summary}.${reason} Vui lòng xem tab Cảnh báo hoặc thử lại sau.` }
}

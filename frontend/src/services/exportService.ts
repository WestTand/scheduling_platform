import { requestBlob } from '@/services/apiClient'

export type ScheduleExportOption = 'schedule-xlsx' | 'schedule-pdf' | 'workload-xlsx'

export const EXPORT_OPTIONS: Array<{ value: ScheduleExportOption; label: string }> = [
  { value: 'schedule-xlsx', label: 'Lịch làm việc (Excel)' },
  { value: 'schedule-pdf', label: 'Lịch làm việc (PDF)' },
  { value: 'workload-xlsx', label: 'Báo cáo workload (Excel)' },
]

const endpointFor = (option: ScheduleExportOption, periodId: string) => {
  const encoded = encodeURIComponent(periodId)
  if (option === 'schedule-pdf') return `/api/admin/schedule-periods/${encoded}/exports/schedule.pdf`
  if (option === 'workload-xlsx') return `/api/admin/schedule-periods/${encoded}/reports/workload.xlsx`
  return `/api/admin/schedule-periods/${encoded}/exports/schedule.xlsx`
}

const fallbackFilename = (option: ScheduleExportOption, periodId: string) => {
  if (option === 'schedule-pdf') return `schedule-period-${periodId}.pdf`
  if (option === 'workload-xlsx') return `workload-period-${periodId}.xlsx`
  return `schedule-period-${periodId}.xlsx`
}

export const exportService = {
  async download(option: ScheduleExportOption, periodId: string) {
    const { blob, filename } = await requestBlob(endpointFor(option, periodId))
    return { blob, filename: filename ?? fallbackFilename(option, periodId) }
  },
}

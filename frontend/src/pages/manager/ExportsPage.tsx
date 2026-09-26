import { Download, FileText } from 'lucide-react'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { apiConfig } from '@/config/api'
import { backendScheduleService } from '@/services/backendScheduleService'
import { EXPORT_OPTIONS, exportService, type ScheduleExportOption } from '@/services/exportService'
import { validateExportSelection } from './ExportsPage.helpers'
import type { WorkSchedule } from '@/types/schedule'

export function ExportsPage() {
  const [exportOption, setExportOption] = useState<ScheduleExportOption>('schedule-xlsx')
  const [periods, setPeriods] = useState<WorkSchedule[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState('')
  const [isDownloading, setIsDownloading] = useState(false)
  const [notice, setNotice] = useState('')
  const [isError, setIsError] = useState(false)

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    backendScheduleService.listPeriods({ status: 'ALL' })
      .then((items) => { if (!ignore) setPeriods(items) })
      .catch((loadError) => {
        if (!ignore) {
          setNotice(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách kỳ lịch.')
          setIsError(true)
        }
      })
    return () => { ignore = true }
  }, [])

  const handleExport = async () => {
    const validationError = validateExportSelection(selectedPeriodId)
    if (validationError) {
      setNotice(validationError)
      setIsError(true)
      return
    }
    setIsDownloading(true)
    try {
      const { blob, filename } = await exportService.download(exportOption, selectedPeriodId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      setNotice(`Đã tải file ${filename}.`)
      setIsError(false)
    } catch (downloadError) {
      setNotice(downloadError instanceof Error ? downloadError.message : 'Không thể xuất file.')
      setIsError(true)
    } finally {
      setIsDownloading(false)
    }
  }

  const selectedLabel = EXPORT_OPTIONS.find((item) => item.value === exportOption)?.label ?? ''
  const selectedPeriod = periods.find((period) => period.id === selectedPeriodId)

  return (
    <div className="space-y-6">
      <PageHeader title="Xuất dữ liệu" description="Tải file lịch làm việc và báo cáo workload từ backend." />

      {notice && <div className={isError ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700' : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'}>{notice}</div>}

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="grid gap-6 p-5 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-5">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Loại file</label>
              <div className="grid gap-2 sm:grid-cols-2">
                {EXPORT_OPTIONS.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setExportOption(item.value)}
                    className={`rounded-xl border px-3 py-2.5 text-left text-sm transition ${exportOption === item.value ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 bg-slate-50 text-slate-700'}`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Kỳ lịch</label>
              <select value={selectedPeriodId} onChange={(event) => setSelectedPeriodId(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
                <option value="">{periods.length ? 'Chọn kỳ lịch' : 'Đang tải kỳ lịch...'}</option>
                {periods.map((period) => <option key={period.id} value={period.id}>{period.name} ({period.startDate} - {period.endDate})</option>)}
              </select>
            </div>

            {apiConfig.useMockApi && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">Chế độ mock: xuất file yêu cầu backend mode.</div>}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="mb-3 flex items-center gap-2 text-slate-700"><FileText className="h-4 w-4" />Tóm tắt cấu hình xuất</div>
            <div className="space-y-3 text-sm text-slate-600">
              <div className="flex justify-between gap-3"><span>Loại file</span><strong>{selectedLabel}</strong></div>
              <div className="flex justify-between gap-3"><span>Kỳ lịch</span><strong>{selectedPeriod ? `${selectedPeriod.name} (${selectedPeriod.startDate} - ${selectedPeriod.endDate})` : 'Chưa chọn'}</strong></div>
            </div>

            <Button className="mt-5 w-full" onClick={() => void handleExport()} disabled={apiConfig.useMockApi || isDownloading}><Download className="mr-2 h-4 w-4" />{isDownloading ? 'Đang xuất...' : 'Xuất dữ liệu'}</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

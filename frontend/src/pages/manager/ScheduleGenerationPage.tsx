import { WandSparkles } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleGenerationForm, type ScheduleGenerationValues } from '@/components/schedule/ScheduleGenerationForm'
import { ScheduleEmployeePoolEditor } from '@/components/schedule/ScheduleEmployeePoolEditor'
import { Button } from '@/components/ui/button'
import { apiConfig } from '@/config/api'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import { backendScheduleService } from '@/services/backendScheduleService'
import { backendShiftTypeService } from '@/services/backendShiftTypeService'
import { buildGenerationNotice, canEditSelectedPeriodPool, canOptimizeSchedulePeriod, getOptimizeErrorMessage, getScheduleGenerationNoticeClass, summarizeOptimizeResult } from './ScheduleGenerationPage.helpers'
import type { Employee } from '@/types/management'
import type { WorkSchedule } from '@/types/schedule'
import { useEffect, useState } from 'react'
import type { ScheduleGenerationNoticeType } from './ScheduleGenerationPage.helpers'
import type { BackendShiftTypeResponse } from '@/types/backendShiftType'
import type { PoolCandidate } from '@/components/schedule/ScheduleEmployeePoolEditor.helpers'
import { getEmployeePoolEditorSessionKey } from '@/components/schedule/ScheduleEmployeePoolEditor.helpers'

type ScheduleGenerationNotice = { type: ScheduleGenerationNoticeType; message: string }

export function ScheduleGenerationPage() {
  const navigate = useNavigate()
  const [pendingValues, setPendingValues] = useState<ScheduleGenerationValues | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [notice, setNotice] = useState<ScheduleGenerationNotice | null>(null)
  const [periodResult, setPeriodResult] = useState<{ periods: WorkSchedule[]; error: string } | null>(null)
  const [selectedPeriodId, setSelectedPeriodId] = useState('')
  const [backendEmployees, setBackendEmployees] = useState<Employee[]>([])
  const [backendShiftTypes, setBackendShiftTypes] = useState<BackendShiftTypeResponse[]>([])
  const [poolEditor, setPoolEditor] = useState<{ open: boolean; periodId: string; poolIds: string[] }>({ open: false, periodId: '', poolIds: [] })

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    backendScheduleService.listPeriods({ status: 'DRAFT' })
      .then((periods) => {
        if (ignore) return
        setPeriodResult({ periods, error: '' })
        setSelectedPeriodId((current) => current || periods[0]?.id || '')
      })
      .catch((loadError) => { if (!ignore) setPeriodResult({ periods: [], error: loadError instanceof Error ? loadError.message : 'Không thể tải kỳ lịch nháp.' }) })
    return () => { ignore = true }
  }, [])

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    Promise.all([
      backendEmployeeService.list({ page: 1, pageSize: 500, status: 'ACTIVE' }),
      backendShiftTypeService.listActive(),
    ]).then(([employeePage, shiftTypes]) => {
      if (ignore) return
      setBackendEmployees(employeePage.data)
      setBackendShiftTypes(shiftTypes)
    }).catch((loadError) => {
      if (!ignore) setNotice({ type: 'error', message: loadError instanceof Error ? loadError.message : 'Không thể tải nhân viên hoặc loại ca backend.' })
    })
    return () => { ignore = true }
  }, [])

  const handleSubmit = (values: ScheduleGenerationValues) => {
    setPendingValues(values)
  }

  const optimizeSelectedPeriod = async () => {
    if (!selectedPeriodId) {
      setNotice({ type: 'error', message: 'Vui lòng chọn một kỳ lịch để tối ưu.' })
      return
    }
    setIsSubmitting(true)
    setNotice(null)
    try {
      const period = periodResult?.periods.find((item) => item.id === selectedPeriodId)
      const result = await backendScheduleService.optimize(selectedPeriodId)
      setNotice({ type: 'success', message: `Đã tối ưu kỳ lịch ${period?.name ?? selectedPeriodId}. ${summarizeOptimizeResult(result)}` })
    } catch (submitError) {
      setNotice({ type: 'error', message: getOptimizeErrorMessage(submitError) })
    } finally {
      setIsSubmitting(false)
    }
  }

  const poolCandidates: PoolCandidate[] = backendEmployees
    .filter((employee) => employee.aiSchedulable === true)
    .map((employee) => ({ id: employee.id, fullName: employee.fullName, employeeCode: employee.employeeCode }))

  const openPoolEditor = async () => {
    if (!selectedPeriodId) return
    setIsSubmitting(true)
    setNotice(null)
    try {
      const detail = await backendScheduleService.getScheduleDetail(selectedPeriodId)
      setPoolEditor({ open: true, periodId: selectedPeriodId, poolIds: detail.employeePoolIds ?? [] })
    } catch (loadError) {
      setNotice({ type: 'error', message: loadError instanceof Error ? loadError.message : 'Không thể tải pool nhân viên.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  const handlePoolSaved = async () => {
    setPoolEditor({ open: false, periodId: '', poolIds: [] })
    setNotice({ type: 'success', message: 'Đã cập nhật pool nhân viên.' })
  }

  const handleConfirm = async () => {
    if (!pendingValues) return
    if (apiConfig.useMockApi) {
      navigate('/manager/schedules/schedule-demo-01')
      return
    }
    setIsSubmitting(true)
    setNotice(null)
    try {
      const generated = await backendScheduleService.generateSchedule({
        periodName: pendingValues.name,
        startDate: pendingValues.startDate,
        endDate: pendingValues.endDate,
        versionNo: 1,
        employeeIds: pendingValues.employeeIds.map(Number),
        shiftTypeIds: pendingValues.shiftCodes.map(Number),
      })
      const generatedPeriod: WorkSchedule = {
        id: String(generated.periodId),
        name: generated.periodName,
        startDate: pendingValues.startDate,
        endDate: pendingValues.endDate,
        createdBy: 'Backend',
        createdAt: new Date().toISOString(),
        employeeCount: pendingValues.employeeIds.length,
        assignmentCount: generated.assignmentCount ?? 0,
        shiftCount: 0,
        status: generated.status as WorkSchedule['status'],
        isMock: false,
      }
      setSelectedPeriodId(generatedPeriod.id)
      setPeriodResult((current) => ({ periods: [generatedPeriod, ...(current?.periods.filter((period) => period.id !== generatedPeriod.id) ?? [])], error: '' }))
      setNotice(buildGenerationNotice(generated.periodName, generated))
      setPendingValues(null)
    } catch (submitError) {
      setNotice({ type: 'error', message: submitError instanceof Error ? submitError.message : 'Không thể tạo kỳ lịch.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Tạo lịch tự động" description="Thiết lập phạm vi và tiêu chí cho yêu cầu lập lịch." />
      <div className="flex items-start gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800"><WandSparkles className="mt-0.5 h-5 w-5 shrink-0" /><p>{apiConfig.useMockApi ? 'Dữ liệu minh họa. Giao diện này chưa chạy thuật toán, chưa kiểm tra ràng buộc và không tạo phân công mới.' : 'Backend mode: form bên dưới tạo kỳ lịch. AI Optimize chỉ chạy được khi kỳ lịch đã có ca làm việc trong backend.'}</p></div>
      {notice && <div className={getScheduleGenerationNoticeClass(notice.type)}>{notice.message}</div>}
      {!apiConfig.useMockApi && <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div className="grid gap-3 md:grid-cols-[1fr_auto_auto_auto] md:items-end"><label className="space-y-1 text-sm text-slate-700">Kỳ lịch để tối ưu<select value={selectedPeriodId} onChange={(event) => setSelectedPeriodId(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none"><option value="">{periodResult ? 'Chọn kỳ lịch' : 'Đang tải kỳ lịch...'}</option>{periodResult?.periods.map((period) => <option key={period.id} value={period.id}>{period.name} ({period.startDate} - {period.endDate})</option>)}</select></label><Button type="button" onClick={optimizeSelectedPeriod} disabled={!canOptimizeSchedulePeriod({ selectedPeriodId, isSubmitting })}>{isSubmitting ? 'Đang tối ưu...' : 'AI Optimize'}</Button><Button type="button" variant="outline" onClick={() => void openPoolEditor()} disabled={!canEditSelectedPeriodPool({ selectedPeriodId, isSubmitting })}>Chỉnh sửa danh sách</Button>{selectedPeriodId && <Button type="button" variant="outline" onClick={() => navigate(`/manager/schedules/${selectedPeriodId}`)}>Xem chi tiết lịch</Button>}</div>{periodResult?.error && <p className="mt-2 text-sm text-red-700">{periodResult.error}</p>}<p className="mt-2 text-xs text-slate-500">Form bên dưới tạo kỳ lịch backend. AI Optimize chạy trên các ca làm việc đã tồn tại trong kỳ lịch được chọn.</p></div>}
      {!apiConfig.useMockApi && <ScheduleEmployeePoolEditor key={getEmployeePoolEditorSessionKey(poolEditor.open)} open={poolEditor.open} periodId={poolEditor.periodId} candidates={poolCandidates} currentPoolIds={poolEditor.poolIds} onCancel={() => setPoolEditor({ open: false, periodId: '', poolIds: [] })} onSaved={() => void handlePoolSaved()} />}
      <ScheduleGenerationForm backendMode={!apiConfig.useMockApi} backendEmployees={backendEmployees} backendShiftTypes={backendShiftTypes} onSubmit={handleSubmit} onCancel={() => navigate('/manager/schedules')} />
      <ConfirmDialog
        open={Boolean(pendingValues)}
        title={apiConfig.useMockApi ? 'Xác nhận tạo lịch mẫu' : 'Xác nhận tạo kỳ lịch'}
        description={apiConfig.useMockApi ? `Yêu cầu “${pendingValues?.name}” sẽ mở kết quả lịch mẫu cố định. Không có thuật toán hoặc phân công mới được chạy.` : `Tạo kỳ lịch backend “${pendingValues?.name}”. Sau khi tạo, bạn có thể chạy AI Optimize cho kỳ lịch này.`}
        confirmLabel={isSubmitting ? 'Đang xử lý...' : apiConfig.useMockApi ? 'Xem kết quả mẫu' : 'Tạo kỳ lịch'}
        onCancel={() => setPendingValues(null)}
        onConfirm={handleConfirm}
      />
    </div>
  )
}

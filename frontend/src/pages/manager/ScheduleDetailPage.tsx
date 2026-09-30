import { AlertTriangle, ArrowLeft, ClipboardCheck, Clock3, Pencil, Send, ShieldCheck, Users, UserCog, XCircle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleCalendar } from '@/components/schedule/ScheduleCalendar'
import { ScheduleEmployeePoolEditor } from '@/components/schedule/ScheduleEmployeePoolEditor'
import { ScheduleSummaryCard } from '@/components/schedule/ScheduleSummaryCard'
import { ScheduleWarningList } from '@/components/schedule/ScheduleWarningList'
import { apiConfig } from '@/config/api'
import { scheduleDetailSeed, scheduleStatusLabels } from '@/mocks/schedules'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import { backendScheduleService } from '@/services/backendScheduleService'
import { canApproveSchedule, getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage, getScheduleDetailStaffingMessage } from './ScheduleDetailPage.helpers'
import type { ScheduleDetail } from '@/types/schedule'
import { ScheduleViewMode } from '@/types/schedule'
import type { ScheduleDetailAction, ScheduleDetailNotice } from './ScheduleDetailPage.helpers'
import type { PoolCandidate } from '@/components/schedule/ScheduleEmployeePoolEditor.helpers'

export function ScheduleDetailPage() {
  const { scheduleId } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<'schedule' | 'warnings'>(() => {
    const tab = searchParams.get('tab')
    return tab === 'warnings' ? tab : 'schedule'
  })
  const [viewMode, setViewMode] = useState<ScheduleViewMode>(ScheduleViewMode.WEEK)
  const [employeeFilter, setEmployeeFilter] = useState('')
  const [shiftFilter, setShiftFilter] = useState('')
  const [dateFilter, setDateFilter] = useState('')
  const [warningOnly, setWarningOnly] = useState(false)
  const [backendResult, setBackendResult] = useState<{ scheduleId: string; detail: ScheduleDetail | null; error: string } | null>(null)
  const [notice, setNotice] = useState<ScheduleDetailNotice | null>(null)
  const [poolCandidates, setPoolCandidates] = useState<PoolCandidate[]>([])
  const [isPoolEditorOpen, setIsPoolEditorOpen] = useState(false)
  const mockDetail = scheduleId === scheduleDetailSeed.id ? scheduleDetailSeed : null
  const detail = apiConfig.useMockApi ? mockDetail : backendResult && scheduleId === backendResult.scheduleId ? backendResult.detail : null

  const loadBackendDetail = async (id: string) => {
    const [period, schedule] = await Promise.all([backendScheduleService.getPeriod(id), backendScheduleService.getScheduleDetail(id)])
    return { ...schedule, createdBy: period.createdBy, createdAt: period.createdAt, status: period.status }
  }

  useEffect(() => {
    if (apiConfig.useMockApi || !scheduleId) return
    let ignore = false
    loadBackendDetail(scheduleId)
      .then((schedule) => { if (!ignore) setBackendResult({ scheduleId, detail: schedule, error: '' }) })
      .catch((loadError) => {
        if (!ignore) {
          const message = loadError instanceof Error ? loadError.message : 'Không thể tải chi tiết lịch.'
          setBackendResult({ scheduleId, detail: null, error: message })
          setNotice({ type: 'error', message })
        }
      })
    return () => { ignore = true }
  }, [scheduleId])

  useEffect(() => {
    if (apiConfig.useMockApi || !scheduleId) return
    let ignore = false
    backendEmployeeService.list({ page: 1, pageSize: 500, status: 'ACTIVE' })
      .then((result) => { if (!ignore) setPoolCandidates(result.data.map((employee) => ({ id: employee.id, fullName: employee.fullName, employeeCode: employee.employeeCode }))) })
      .catch(() => { if (!ignore) setPoolCandidates([]) })
    return () => { ignore = true }
  }, [scheduleId])

  const isLoading = !apiConfig.useMockApi && Boolean(scheduleId) && (!backendResult || backendResult.scheduleId !== scheduleId)
  const error = !apiConfig.useMockApi && backendResult && backendResult.scheduleId === scheduleId ? backendResult.error : ''

  const employees = useMemo(() => detail?.employeePool ?? [], [detail])
  const shifts = useMemo(() => [...new Set(detail?.assignments.map((assignment) => assignment.shiftCode) ?? [])], [detail])

  const runScheduleAction = async (action: ScheduleDetailAction) => {
    if (!detail || apiConfig.useMockApi) return
    try {
      if (action === 'approve') await backendScheduleService.approvePeriod(detail.id)
      if (action === 'publish') await backendScheduleService.publishPeriod(detail.id)
      if (action === 'cancel') await backendScheduleService.cancelPeriod(detail.id)
      const refreshed = await loadBackendDetail(detail.id)
      setBackendResult({ scheduleId: detail.id, detail: refreshed, error: '' })
      setNotice({ type: 'success', message: 'Đã cập nhật trạng thái lịch.' })
    } catch (actionError) {
      setNotice({ type: 'error', message: actionError instanceof Error ? actionError.message : 'Không thể cập nhật trạng thái lịch.' })
    }
  }

  if (isLoading) {
    return <div className="rounded-xl border border-slate-200 bg-white px-4 py-8 text-center text-sm text-slate-600">Đang tải chi tiết lịch...</div>
  }

  if (!detail) {
    return <EmptyState title="Không tìm thấy lịch" description={error || 'Không có dữ liệu lịch phù hợp.'} action={<Button onClick={() => navigate('/manager/schedules')}>Quay lại danh sách</Button>} />
  }

  const readOnlyMessage = getScheduleDetailReadOnlyMessage(detail.status, detail.optimizationStatus)
  const staffingMessage = getScheduleDetailStaffingMessage(detail.optimizationStatus, detail.staffingAdvice)
  const canEditEmployeePool = detail.status === 'DRAFT'
  const statusActions = apiConfig.useMockApi ? [] : getScheduleDetailActions(detail.status)
  const isInfeasible = detail.optimizationStatus === 'INFEASIBLE'

  const reloadDetail = async () => {
    const refreshed = await loadBackendDetail(detail.id)
    setBackendResult({ scheduleId: detail.id, detail: refreshed, error: '' })
  }

  const handlePoolSaved = async () => {
    setIsPoolEditorOpen(false)
    try {
      await reloadDetail()
      setNotice({ type: 'success', message: 'Đã cập nhật pool nhân viên.' })
    } catch (reloadError) {
      setNotice({ type: 'error', message: reloadError instanceof Error ? reloadError.message : 'Không thể tải lại chi tiết lịch.' })
    }
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" asChild><Link to="/manager/schedules"><ArrowLeft className="mr-2 h-4 w-4" />Quay lại danh sách lịch</Link></Button>
      <PageHeader title={detail.name} description={`${detail.startDate} - ${detail.endDate} · Người tạo: ${detail.createdBy}`} />
      <div className="flex flex-wrap items-center gap-2"><Badge>{scheduleStatusLabels[detail.status]}</Badge>{isInfeasible && <Badge variant="destructive">INFE</Badge>}{detail.isMock && <Badge variant="outline">Dữ liệu minh họa</Badge>}<span className="text-sm text-slate-500">Tạo lúc {new Date(detail.createdAt).toLocaleString('vi-VN')}</span></div>
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="outline" asChild><Link to={`/manager/schedules/${detail.id}/edit`}><Pencil className="mr-2 h-4 w-4" />Chỉnh sửa</Link></Button>
        {statusActions.includes('approve') && <Button variant="outline" disabled={!canApproveSchedule(detail.status, detail.optimizationStatus)} title={isInfeasible ? 'Lịch đang bị cảnh báo (INFE) nên không thể duyệt.' : undefined} onClick={() => void runScheduleAction('approve')}><ShieldCheck className="mr-2 h-4 w-4" />Duyệt</Button>}
        {statusActions.includes('publish') && <Button variant="outline" onClick={() => void runScheduleAction('publish')}><Send className="mr-2 h-4 w-4" />Công bố</Button>}
        {statusActions.includes('cancel') && <Button variant="outline" className="text-red-600" onClick={() => void runScheduleAction('cancel')}><XCircle className="mr-2 h-4 w-4" />Hủy</Button>}
      </div>
      {notice && <div className={getScheduleDetailNoticeClass(notice.type)}>{notice.message}</div>}
      {readOnlyMessage && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{readOnlyMessage}</div>}
      {staffingMessage && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{staffingMessage}</div>}
      {canEditEmployeePool && !apiConfig.useMockApi && (
        <div>
          <Button variant="outline" onClick={() => setIsPoolEditorOpen(true)}><UserCog className="mr-2 h-4 w-4" />Chỉnh sửa pool nhân viên</Button>
          <ScheduleEmployeePoolEditor
            open={isPoolEditorOpen}
            periodId={detail.id}
            candidates={poolCandidates}
            currentPoolIds={detail.employeePoolIds ?? []}
            onCancel={() => setIsPoolEditorOpen(false)}
            onSaved={() => void handlePoolSaved()}
          />
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <ScheduleSummaryCard label="Tổng nhân viên" value={detail.employeeCount} icon={<Users className="h-5 w-5" />} tone="blue" />
        <ScheduleSummaryCard label="Tổng phân công" value={detail.assignmentCount} icon={<ClipboardCheck className="h-5 w-5" />} tone="green" />
        <ScheduleSummaryCard label="Tổng số ca" value={detail.shiftCount} icon={<Clock3 className="h-5 w-5" />} tone="amber" />
        <ScheduleSummaryCard label="Số cảnh báo" value={detail.warnings.length} icon={<AlertTriangle className="h-5 w-5" />} tone="red" />
      </section>

      <Card className="border-slate-200 shadow-sm"><CardContent className="p-2"><div className="flex flex-wrap gap-2">{[['schedule', 'Lịch làm việc'], ['warnings', 'Cảnh báo']].map(([value, label]) => <Button key={value} variant={activeTab === value ? 'default' : 'ghost'} onClick={() => { setActiveTab(value as typeof activeTab); const params = new URLSearchParams(searchParams); if (value === 'schedule') { params.delete('tab') } else { params.set('tab', value) } setSearchParams(params) }}>{label}</Button>)}</div></CardContent></Card>

      {activeTab === 'schedule' && <div className="space-y-4"><Card className="border-slate-200 shadow-sm"><CardContent className="grid gap-3 p-4 md:grid-cols-4"><select value={viewMode} onChange={(event) => setViewMode(event.target.value as ScheduleViewMode)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value={ScheduleViewMode.WEEK}>Xem theo tuần</option><option value={ScheduleViewMode.DAILY}>Xem theo ngày</option><option value={ScheduleViewMode.LIST}>Xem dạng danh sách</option></select><select value={employeeFilter} onChange={(event) => setEmployeeFilter(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value="">Tất cả nhân viên</option>{employees.map((employee) => <option key={employee} value={employee}>{employee}</option>)}</select><select value={shiftFilter} onChange={(event) => setShiftFilter(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value="">Tất cả loại ca</option>{shifts.map((code) => <option key={code} value={code}>{code}</option>)}</select><div className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><input type="date" value={dateFilter} onChange={(event) => setDateFilter(event.target.value)} /><label className="ml-auto flex items-center gap-2 whitespace-nowrap"><input type="checkbox" checked={warningOnly} onChange={(event) => setWarningOnly(event.target.checked)} />Có cảnh báo</label></div></CardContent></Card><ScheduleCalendar assignments={detail.assignments} mode={viewMode} employeeFilter={employeeFilter} shiftFilter={shiftFilter} dateFilter={dateFilter} warningOnly={warningOnly} /></div>}
      {activeTab === 'warnings' && <ScheduleWarningList warnings={detail.warnings} />}
    </div>
  )
}

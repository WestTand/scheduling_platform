import { AlertTriangle, ArrowLeft, CalendarDays, ClipboardCheck, Clock3, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { EmptyState } from '@/components/common/EmptyState'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleCalendar } from '@/components/schedule/ScheduleCalendar'
import { ScheduleEvaluationPanel } from '@/components/schedule/ScheduleEvaluationPanel'
import { ScheduleSummaryCard } from '@/components/schedule/ScheduleSummaryCard'
import { ScheduleWarningList } from '@/components/schedule/ScheduleWarningList'
import { scheduleDetailSeed, scheduleStatusLabels } from '@/mocks/schedules'
import { ScheduleViewMode } from '@/types/schedule'

export function ScheduleDetailPage() {
  const { scheduleId } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<'schedule' | 'warnings' | 'evaluation'>(() => {
    const tab = searchParams.get('tab')
    return tab === 'warnings' || tab === 'evaluation' ? tab : 'schedule'
  })
  const [viewMode, setViewMode] = useState<ScheduleViewMode>(ScheduleViewMode.WEEK)
  const [employeeFilter, setEmployeeFilter] = useState('')
  const [shiftFilter, setShiftFilter] = useState('')
  const [dateFilter, setDateFilter] = useState('')
  const [warningOnly, setWarningOnly] = useState(false)
  const detail = scheduleId === scheduleDetailSeed.id ? scheduleDetailSeed : null

  const employees = useMemo(() => [...new Set(detail?.assignments.flatMap((assignment) => assignment.employeeNames) ?? [])], [detail])
  const shifts = useMemo(() => [...new Map(detail?.assignments.map((assignment) => [assignment.shiftCode, assignment.shiftName]) ?? []).entries()], [detail])

  if (!detail) {
    return <EmptyState title="Không tìm thấy lịch" description="Lịch này không có trong bộ dữ liệu minh họa." action={<Button onClick={() => navigate('/manager/schedules')}>Quay lại danh sách</Button>} />
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" asChild><Link to="/manager/schedules"><ArrowLeft className="mr-2 h-4 w-4" />Quay lại danh sách lịch</Link></Button>
      <PageHeader title={detail.name} description={`${detail.startDate} - ${detail.endDate} · Người tạo: ${detail.createdBy}`} />
      <div className="flex flex-wrap items-center gap-2"><Badge>{scheduleStatusLabels[detail.status]}</Badge><Badge variant="outline">Dữ liệu minh họa</Badge><span className="text-sm text-slate-500">Tạo lúc {new Date(detail.createdAt).toLocaleString('vi-VN')}</span></div>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <ScheduleSummaryCard label="Tổng nhân viên" value={detail.employeeCount} icon={<Users className="h-5 w-5" />} tone="blue" />
        <ScheduleSummaryCard label="Tổng phân công" value={detail.assignmentCount} icon={<ClipboardCheck className="h-5 w-5" />} tone="green" />
        <ScheduleSummaryCard label="Tổng số ca" value={detail.shiftCount} icon={<Clock3 className="h-5 w-5" />} tone="amber" />
        <ScheduleSummaryCard label="Số cảnh báo" value={detail.warnings.length} icon={<AlertTriangle className="h-5 w-5" />} tone="red" />
        <ScheduleSummaryCard label="Điểm công bằng mẫu" value={detail.evaluation.fairnessPenalty} icon={<CalendarDays className="h-5 w-5" />} tone="blue" />
      </section>

      <Card className="border-slate-200 shadow-sm"><CardContent className="p-2"><div className="flex flex-wrap gap-2">{[['schedule', 'Lịch làm việc'], ['warnings', 'Cảnh báo'], ['evaluation', 'Đánh giá']].map(([value, label]) => <Button key={value} variant={activeTab === value ? 'default' : 'ghost'} onClick={() => { setActiveTab(value as typeof activeTab); const params = new URLSearchParams(searchParams); if (value === 'schedule') { params.delete('tab') } else { params.set('tab', value) } setSearchParams(params) }}>{label}</Button>)}</div></CardContent></Card>

      {activeTab === 'schedule' && <div className="space-y-4"><Card className="border-slate-200 shadow-sm"><CardContent className="grid gap-3 p-4 md:grid-cols-4"><select value={viewMode} onChange={(event) => setViewMode(event.target.value as ScheduleViewMode)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value={ScheduleViewMode.WEEK}>Xem theo tuần</option><option value={ScheduleViewMode.DAY}>Xem theo ngày</option><option value={ScheduleViewMode.LIST}>Xem dạng danh sách</option></select><select value={employeeFilter} onChange={(event) => setEmployeeFilter(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value="">Tất cả nhân viên</option>{employees.map((employee) => <option key={employee} value={employee}>{employee}</option>)}</select><select value={shiftFilter} onChange={(event) => setShiftFilter(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><option value="">Tất cả loại ca</option>{shifts.map(([code, name]) => <option key={code} value={code}>{name}</option>)}</select><div className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><input type="date" value={dateFilter} onChange={(event) => setDateFilter(event.target.value)} /><label className="ml-auto flex items-center gap-2 whitespace-nowrap"><input type="checkbox" checked={warningOnly} onChange={(event) => setWarningOnly(event.target.checked)} />Có cảnh báo</label></div></CardContent></Card><ScheduleCalendar assignments={detail.assignments} mode={viewMode} employeeFilter={employeeFilter} shiftFilter={shiftFilter} dateFilter={dateFilter} warningOnly={warningOnly} /></div>}
      {activeTab === 'warnings' && <ScheduleWarningList warnings={detail.warnings} />}
      {activeTab === 'evaluation' && <ScheduleEvaluationPanel evaluation={detail.evaluation} />}
    </div>
  )
}

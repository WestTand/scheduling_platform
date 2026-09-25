import { ChevronLeft, ChevronRight, CircleDashed, Clock3, Filter } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { formatShiftRangeLabel, getShiftDefinition, shiftDefinitions } from '@/configs/shiftDefinitions'
import { apiConfig } from '@/config/api'
import { useAuth } from '@/contexts/useAuth'
import { scheduleDetailSeed } from '@/mocks/schedules'
import { employeeScheduleService } from '@/services/employeeScheduleService'
import { adaptBackendEmployeeSchedule, getEmployeeWeekWindow } from './EmployeeSchedulePage.helpers'

const shiftOptions = [{ value: 'ALL', label: 'Tất cả ca' }, ...shiftDefinitions.map((shift) => ({ value: shift.code, label: shift.name }))]
const weeklySchedule = scheduleDetailSeed.assignments.map((assignment) => ({
  ...assignment,
  day: new Date(`${assignment.date}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'long' }),
  status: assignment.warningCount > 0 ? 'Lưu ý' : 'Đã xác nhận',
  warning: assignment.warningCount > 0 ? 'Có cảnh báo từ dữ liệu lịch' : '',
}))

export function EmployeeSchedulePage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedShift, setSelectedShift] = useState('ALL')
  const [weekOffset, setWeekOffset] = useState(0)
  const [backendItems, setBackendItems] = useState<ReturnType<typeof adaptBackendEmployeeSchedule>>([])
  const [isLoading, setIsLoading] = useState(() => !apiConfig.useMockApi)
  const [loadError, setLoadError] = useState('')

  const weekWindow = useMemo(() => getEmployeeWeekWindow(weekOffset), [weekOffset])

  const changeWeek = (delta: number) => {
    if (!apiConfig.useMockApi) setIsLoading(true)
    setWeekOffset((current) => current + delta)
  }

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    employeeScheduleService.list(weekWindow)
      .then((items) => { if (!ignore) { setBackendItems(adaptBackendEmployeeSchedule(items)); setLoadError('') } })
      .catch((loadErr) => { if (!ignore) setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải lịch làm việc.') })
      .finally(() => { if (!ignore) setIsLoading(false) })
    return () => { ignore = true }
  }, [weekWindow])

  const baseSchedule = apiConfig.useMockApi ? weeklySchedule : backendItems
  const filteredSchedule = useMemo(() => baseSchedule.filter((item) => selectedShift === 'ALL' || item.shiftCode === selectedShift), [baseSchedule, selectedShift])
  const selectedAssignment = baseSchedule.find((item) => item.id === searchParams.get('assignmentId'))
  const view = searchParams.get('view') ?? 'week'

  const closeAssignment = () => {
    const params = new URLSearchParams(searchParams)
    params.delete('assignmentId')
    setSearchParams(params, { replace: true })
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Lịch làm việc cá nhân" description={`Lịch của ${user?.name ?? 'nhân viên'} ${view === 'today' ? 'trong hôm nay' : 'trong tuần hiện tại'}.`} />

      <section className="grid gap-4 md:grid-cols-4">
        <Link to="/employee/schedule?view=today" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"><p className="text-sm text-slate-500">Ca làm hôm nay</p><p className="mt-2 text-xl font-semibold">{baseSchedule[0] ? getShiftDefinition(baseSchedule[0].shiftCode)?.name : '—'}</p><p className="text-sm text-slate-500">{baseSchedule[0] && getShiftDefinition(baseSchedule[0].shiftCode) ? formatShiftRangeLabel(getShiftDefinition(baseSchedule[0].shiftCode)!) : weekWindow.from}</p></Link>
        <Link to="/employee/schedule?view=week" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"><p className="text-sm text-slate-500">Tổng số ca</p><p className="mt-2 text-xl font-semibold">{baseSchedule.length}</p><p className="text-sm text-slate-500">Tuần này</p></Link>
        <Link to="/employee/schedule?view=week" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"><p className="text-sm text-slate-500">Tổng giờ làm</p><p className="mt-2 text-xl font-semibold">{Math.round(baseSchedule.reduce((total, item) => total + (getShiftDefinition(item.shiftCode)?.workMinutes ?? 0), 0) / 60)}h</p><p className="text-sm text-slate-500">Tuần này</p></Link>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Cảnh báo</p><p className="mt-2 text-xl font-semibold">{baseSchedule.filter((item) => item.warning).length}</p><p className="text-sm text-slate-500">Cần xem lại</p></div>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon" onClick={() => changeWeek(-1)}><ChevronLeft className="h-4 w-4" /></Button>
              <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium">Tuần {weekOffset === 0 ? 'hiện tại' : `${weekOffset + 1}`}</div>
              <Button variant="outline" size="icon" onClick={() => changeWeek(1)}><ChevronRight className="h-4 w-4" /></Button>
            </div>
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-slate-500" />
              <select value={selectedShift} onChange={(event) => setSelectedShift(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
                {shiftOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </div>
          </div>

          {!apiConfig.useMockApi && isLoading && <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">Đang tải lịch làm việc...</div>}
          {!apiConfig.useMockApi && loadError && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>}
          {!apiConfig.useMockApi && !isLoading && !loadError && filteredSchedule.length === 0 && <div className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-center text-sm text-slate-600">Bạn chưa có lịch làm việc trong khoảng thời gian này.</div>}
          <div className="space-y-3">
            {filteredSchedule.map((item) => (
              <Link to={`/employee/schedule?assignmentId=${item.id}`} key={item.date} className="flex cursor-pointer flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="font-medium text-slate-900">{item.day} • {item.date}</p>
                  <p className="text-sm text-slate-500">{getShiftDefinition(item.shiftCode)?.name} • {formatShiftRangeLabel(getShiftDefinition(item.shiftCode)!)}</p>
                  {item.warning && <p className="mt-2 inline-flex items-center gap-2 text-xs text-amber-700"><CircleDashed className="h-3.5 w-3.5" />{item.warning}</p>}
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <Clock3 className="h-4 w-4 text-slate-500" />
                  <span className="rounded-full bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-700">{item.status}</span>
                </div>
              </Link>
            ))}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog open={Boolean(selectedAssignment)} title="Chi tiết ca làm" description="Thông tin ca làm mẫu trong lịch cá nhân." onCancel={closeAssignment} onConfirm={closeAssignment} confirmLabel="Đóng">
        {selectedAssignment && <div className="mt-4 space-y-3 text-sm text-slate-600"><div className="flex justify-between"><span>Ngày</span><strong>{selectedAssignment.day} {selectedAssignment.date}</strong></div><div className="flex justify-between"><span>Loại ca</span><strong>{getShiftDefinition(selectedAssignment.shiftCode)?.name}</strong></div><div className="flex justify-between"><span>Thời gian</span><strong>{formatShiftRangeLabel(getShiftDefinition(selectedAssignment.shiftCode)!)}</strong></div><div className="flex justify-between"><span>Tổng thời lượng</span><strong>{(getShiftDefinition(selectedAssignment.shiftCode)?.workMinutes ?? 0) / 60} giờ</strong></div><div className="flex justify-between"><span>Trạng thái</span><strong>{selectedAssignment.status}</strong></div><div className="flex justify-between"><span>Ghi chú</span><strong>{selectedAssignment.warning || 'Không có'}</strong></div></div>}
      </ConfirmDialog>
    </div>
  )
}

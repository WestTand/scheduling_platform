import { ChevronLeft, ChevronRight, CircleDashed, Clock3, Filter } from 'lucide-react'
import { useMemo, useState } from 'react'

import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useAuth } from '@/contexts/useAuth'

const shiftOptions = [
  { value: 'ALL', label: 'Tất cả ca' },
  { value: 'DAY', label: 'Ca sáng' },
  { value: 'EVENING', label: 'Ca chiều' },
  { value: 'NIGHT', label: 'Ca đêm' },
]

const weeklySchedule = [
  { day: 'Thứ 2', date: '2026-09-14', shift: 'Ca sáng', time: '07:00 - 12:00', status: 'Đã xác nhận', warning: '' },
  { day: 'Thứ 3', date: '2026-09-15', shift: 'Ca chiều', time: '13:00 - 18:00', status: 'Đã xác nhận', warning: 'Cần nghỉ ngắn giữa ca' },
  { day: 'Thứ 4', date: '2026-09-16', shift: 'Ca đêm', time: '19:00 - 23:00', status: 'Lưu ý', warning: 'Ca đêm có số giờ hạn chế' },
  { day: 'Thứ 5', date: '2026-09-17', shift: 'Ca sáng', time: '07:00 - 12:00', status: 'Đã xác nhận', warning: '' },
  { day: 'Thứ 6', date: '2026-09-18', shift: 'Ca chiều', time: '13:00 - 18:00', status: 'Chờ xác nhận', warning: '' },
]

export function EmployeeSchedulePage() {
  const { user } = useAuth()
  const [selectedShift, setSelectedShift] = useState('ALL')
  const [weekOffset, setWeekOffset] = useState(0)

  const filteredSchedule = useMemo(() => weeklySchedule.filter((item) => selectedShift === 'ALL' || item.shift.includes(selectedShift === 'DAY' ? 'Ca sáng' : selectedShift === 'EVENING' ? 'Ca chiều' : 'Ca đêm')), [selectedShift])

  return (
    <div className="space-y-6">
      <PageHeader title="Lịch làm việc cá nhân" description={`Lịch của ${user?.name ?? 'nhân viên'} trong tuần hiện tại.`} />

      <section className="grid gap-4 md:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Ca làm hôm nay</p><p className="mt-2 text-xl font-semibold">Ca sáng</p><p className="text-sm text-slate-500">07:00 - 12:00</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Ca tiếp theo</p><p className="mt-2 text-xl font-semibold">Ca chiều</p><p className="text-sm text-slate-500">13:00 - 18:00</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Tổng giờ làm</p><p className="mt-2 text-xl font-semibold">42h</p><p className="text-sm text-slate-500">Tuần này</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Cảnh báo</p><p className="mt-2 text-xl font-semibold">1</p><p className="text-sm text-slate-500">Cần xem lại</p></div>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon" onClick={() => setWeekOffset((current) => current - 1)}><ChevronLeft className="h-4 w-4" /></Button>
              <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium">Tuần {weekOffset === 0 ? 'hiện tại' : `${weekOffset + 1}`}</div>
              <Button variant="outline" size="icon" onClick={() => setWeekOffset((current) => current + 1)}><ChevronRight className="h-4 w-4" /></Button>
            </div>
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-slate-500" />
              <select value={selectedShift} onChange={(event) => setSelectedShift(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
                {shiftOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </div>
          </div>

          <div className="space-y-3">
            {filteredSchedule.map((item) => (
              <div key={item.date} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="font-medium text-slate-900">{item.day} • {item.date}</p>
                  <p className="text-sm text-slate-500">{item.shift} • {item.time}</p>
                  {item.warning && <p className="mt-2 inline-flex items-center gap-2 text-xs text-amber-700"><CircleDashed className="h-3.5 w-3.5" />{item.warning}</p>}
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <Clock3 className="h-4 w-4 text-slate-500" />
                  <span className="rounded-full bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-700">{item.status}</span>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

import { CalendarPlus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleList } from '@/components/schedule/ScheduleList'
import { scheduleSeed, scheduleStatusLabels } from '@/mocks/schedules'
import type { ScheduleStatus } from '@/types/schedule'

export function SchedulesPage() {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<'ALL' | ScheduleStatus>('ALL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [notice, setNotice] = useState('')

  const schedules = useMemo(() => scheduleSeed.filter((schedule) => {
    const matchesSearch = !search || schedule.name.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = status === 'ALL' || schedule.status === status
    const matchesStart = !startDate || schedule.endDate >= startDate
    const matchesEnd = !endDate || schedule.startDate <= endDate
    return matchesSearch && matchesStatus && matchesStart && matchesEnd
  }), [search, status, startDate, endDate])

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý lịch làm việc" description="Danh sách lịch mẫu để xem xét giao diện quản lý lịch." action={<Button onClick={() => navigate('/manager/schedule-generation')}><CalendarPlus className="mr-2 h-4 w-4" />Tạo lịch mới</Button>} />
      <div className="flex flex-wrap items-center gap-2"><Badge variant="outline">Dữ liệu minh họa</Badge><span className="text-sm text-slate-500">Các thao tác chỉnh sửa, duyệt và công bố sẽ được triển khai trong Phase 4.</span></div>
      <Card className="border-slate-200 shadow-sm"><CardContent className="grid gap-3 p-4 md:grid-cols-[1.4fr_0.8fr_0.8fr_0.8fr]"><label className="relative"><Search className="absolute left-3 top-3 h-4 w-4 text-slate-400" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo tên lịch" className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3 text-sm focus:border-blue-500 focus:outline-none" /></label><input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" /><input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" /><select value={status} onChange={(event) => setStatus(event.target.value as 'ALL' | ScheduleStatus)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"><option value="ALL">Tất cả trạng thái</option>{Object.entries(scheduleStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></CardContent></Card>
      {notice && <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</div>}
      <ScheduleList schedules={schedules} onPlaceholderAction={(label) => setNotice(`${label} sẽ được triển khai trong Phase 4.`)} />
    </div>
  )
}

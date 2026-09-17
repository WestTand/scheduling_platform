import { CalendarPlus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleList } from '@/components/schedule/ScheduleList'
import { scheduleSeed, scheduleStatusLabels } from '@/mocks/schedules'
import type { ScheduleStatus } from '@/types/schedule'

export function SchedulesPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState(() => searchParams.get('search') ?? '')
  const [status, setStatus] = useState<'ALL' | ScheduleStatus>(() => (searchParams.get('status') as ScheduleStatus | 'ALL' | null) ?? 'ALL')
  const [startDate, setStartDate] = useState(() => searchParams.get('startDate') ?? '')
  const [endDate, setEndDate] = useState(() => searchParams.get('endDate') ?? '')
  const [notice, setNotice] = useState('')

  const schedules = useMemo(() => scheduleSeed.filter((schedule) => {
    const matchesSearch = !search || schedule.name.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = status === 'ALL' || schedule.status === status
    const matchesStart = !startDate || schedule.endDate >= startDate
    const matchesEnd = !endDate || schedule.startDate <= endDate
    const matchesWarnings = searchParams.get('hasWarnings') !== 'true' || schedule.id === 'schedule-demo-01'
    return matchesSearch && matchesStatus && matchesStart && matchesEnd && matchesWarnings
  }), [search, status, startDate, endDate, searchParams])

  const updateFilters = (next: Record<string, string>) => {
    const params = new URLSearchParams(searchParams)
    Object.entries(next).forEach(([key, value]) => value ? params.set(key, value) : params.delete(key))
    setSearchParams(params)
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý lịch làm việc" description="Danh sách lịch mẫu để xem xét giao diện quản lý lịch." action={<Button onClick={() => navigate('/manager/schedule-generation')}><CalendarPlus className="mr-2 h-4 w-4" />Tạo lịch mới</Button>} />
      <div className="flex flex-wrap items-center gap-2"><Badge variant="outline">Dữ liệu minh họa</Badge><span className="text-sm text-slate-500">Các thao tác chỉnh sửa, duyệt và công bố sẽ được triển khai trong Phase 4.</span></div>
      <Card className="border-slate-200 shadow-sm"><CardContent className="grid gap-3 p-4 md:grid-cols-[1.4fr_0.8fr_0.8fr_0.8fr]"><label className="relative"><Search className="absolute left-3 top-3 h-4 w-4 text-slate-400" /><input value={search} onChange={(event) => { setSearch(event.target.value); updateFilters({ search: event.target.value }) }} placeholder="Tìm theo tên lịch" className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3 text-sm focus:border-blue-500 focus:outline-none" /></label><input type="date" value={startDate} onChange={(event) => { setStartDate(event.target.value); updateFilters({ startDate: event.target.value }) }} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" /><input type="date" value={endDate} onChange={(event) => { setEndDate(event.target.value); updateFilters({ endDate: event.target.value }) }} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" /><select value={status} onChange={(event) => { const value = event.target.value as 'ALL' | ScheduleStatus; setStatus(value); updateFilters({ status: value }) }} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"><option value="ALL">Tất cả trạng thái</option>{Object.entries(scheduleStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></CardContent></Card>
      {notice && <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</div>}
      <ScheduleList schedules={schedules} onPlaceholderAction={(label) => setNotice(`${label} sẽ được triển khai trong Phase 4.`)} />
    </div>
  )
}

import { CalendarDays, Eye, MoreHorizontal } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { scheduleStatusLabels } from '@/mocks/schedules'
import type { WorkSchedule } from '@/types/schedule'

interface ScheduleListProps {
  schedules: WorkSchedule[]
  onPlaceholderAction: (label: string) => void
}

export function ScheduleList({ schedules, onPlaceholderAction }: ScheduleListProps) {
  if (!schedules.length) {
    return <div className="rounded-xl border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500">Không có lịch phù hợp với bộ lọc.</div>
  }

  return (
    <div className="grid gap-4">
      {schedules.map((schedule) => (
        <Card key={schedule.id} className="border-slate-200 shadow-sm">
          <CardContent className="flex flex-col gap-4 p-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-semibold text-slate-900">{schedule.name}</h2>
                <Badge variant="outline">Dữ liệu minh họa</Badge>
              </div>
              <div className="mt-2 grid gap-2 text-sm text-slate-500 sm:grid-cols-2 lg:grid-cols-4">
                <span><CalendarDays className="mr-1 inline h-4 w-4" />{schedule.startDate} - {schedule.endDate}</span>
                <span>Người tạo: {schedule.createdBy}</span>
                <span>Tạo lúc: {new Date(schedule.createdAt).toLocaleString('vi-VN')}</span>
                <span>{schedule.employeeCount} nhân viên · {schedule.assignmentCount} phân công</span>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={schedule.status === 'PUBLISHED' ? 'default' : schedule.status === 'DRAFT' ? 'secondary' : 'outline'}>{scheduleStatusLabels[schedule.status]}</Badge>
              <Button variant="outline" size="sm" asChild><Link to={`/manager/schedules/${schedule.id}`}><Eye className="mr-2 h-4 w-4" />Xem chi tiết</Link></Button>
              <Button variant="ghost" size="icon" onClick={() => onPlaceholderAction('Mở lịch')} aria-label="Mở lịch"><MoreHorizontal className="h-4 w-4" /></Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

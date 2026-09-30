import { CalendarDays, Eye, Pencil, Send, ShieldCheck, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { scheduleStatusLabels } from '@/mocks/schedules'
import { canApproveSchedule } from '@/pages/manager/ScheduleDetailPage.helpers'
import { ScheduleStatus, type WorkSchedule } from '@/types/schedule'

interface ScheduleListProps {
  schedules: WorkSchedule[]
  onApprove: (schedule: WorkSchedule) => void
  onPublish: (schedule: WorkSchedule) => void
  onCancel: (schedule: WorkSchedule) => void
}

const formatScheduleCreatedAt = (value: string) => value ? new Date(value).toLocaleString('vi-VN') : 'Chưa ghi nhận thời điểm tạo'

export function ScheduleList({ schedules, onApprove, onPublish, onCancel }: ScheduleListProps) {
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
                {schedule.isMock && <Badge variant="outline">Dữ liệu minh họa</Badge>}
              </div>
              <div className="mt-2 grid gap-2 text-sm text-slate-500 sm:grid-cols-2 lg:grid-cols-4">
                <span><CalendarDays className="mr-1 inline h-4 w-4" />{schedule.startDate} - {schedule.endDate}</span>
                <span>Người tạo: {schedule.createdBy}</span>
                <span>Tạo lúc: {formatScheduleCreatedAt(schedule.createdAt)}</span>
                <span>{schedule.employeeCount} nhân viên · {schedule.assignmentCount} phân công</span>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={schedule.status === 'PUBLISHED' ? 'default' : schedule.status === 'DRAFT' ? 'secondary' : 'outline'}>{scheduleStatusLabels[schedule.status]}</Badge>
              {schedule.optimizationStatus === 'INFEASIBLE' && <Badge variant="destructive">INFE</Badge>}
              <Button variant="outline" size="sm" asChild><Link to={`/manager/schedules/${schedule.id}`}><Eye className="mr-2 h-4 w-4" />Xem chi tiết</Link></Button>
              <Button variant="outline" size="sm" asChild><Link to={`/manager/schedules/${schedule.id}/edit`}><Pencil className="mr-2 h-4 w-4" />Chỉnh sửa</Link></Button>
              {schedule.status === ScheduleStatus.DRAFT && <Button variant="outline" size="sm" disabled={!canApproveSchedule(schedule.status, schedule.optimizationStatus)} title={schedule.optimizationStatus === 'INFEASIBLE' ? 'Lịch đang bị cảnh báo (INFE) nên không thể duyệt.' : undefined} onClick={() => onApprove(schedule)}><ShieldCheck className="mr-2 h-4 w-4" />Duyệt</Button>}
              {schedule.status === ScheduleStatus.APPROVED && <Button variant="outline" size="sm" onClick={() => onPublish(schedule)}><Send className="mr-2 h-4 w-4" />Công bố</Button>}
              {(schedule.status === ScheduleStatus.DRAFT || schedule.status === ScheduleStatus.APPROVED) && <Button variant="outline" size="sm" className="text-red-600" onClick={() => onCancel(schedule)}><XCircle className="mr-2 h-4 w-4" />Hủy</Button>}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

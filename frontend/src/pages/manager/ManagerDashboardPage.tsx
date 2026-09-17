import { AlertTriangle, ArrowRight, CalendarCheck2, CheckCircle2, Clock3, FileWarning, Sparkles, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { currentDateLabel, managerDashboardData } from '@/mocks/dashboardData'
import { leaveRequestSeed, preferenceSeed, swapRequestSeed } from '@/mocks/managementData'
import { scheduleDetailSeed, scheduleSeed } from '@/mocks/schedules'
import { useAuth } from '@/contexts/useAuth'

export function ManagerDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const quickActions = [
    { label: 'Tạo lịch tự động', icon: Sparkles, path: '/manager/schedule-generation' },
    { label: 'Xem lịch làm việc', icon: CalendarCheck2, path: '/manager/schedules' },
    { label: 'Xử lý yêu cầu nghỉ', icon: CheckCircle2, path: '/manager/leave-requests?status=PENDING' },
    { label: 'Xử lý đổi ca', icon: Clock3, path: '/manager/swap-requests?status=PENDING' },
    { label: 'Xuất dữ liệu', icon: FileWarning, path: '/manager/exports' },
  ]

  const summaryLinks = ['/manager/employees', '/manager/schedules?view=week', '/manager/dashboard#action-center', '/manager/schedules?hasWarnings=true']

  const pendingSchedule = scheduleSeed.find((schedule) => schedule.status === 'DRAFT')
  const pendingLeave = leaveRequestSeed.find((request) => request.status === 'PENDING')
  const pendingSwap = swapRequestSeed.find((request) => request.status === 'PENDING')
  const pendingPreference = preferenceSeed.find((preference) => preference.status === 'PENDING')
  const criticalWarning = scheduleDetailSeed.warnings.find((warning) => warning.severity === 'HIGH')
  const actionItems = [
    { title: 'Lịch chờ duyệt', related: pendingSchedule?.name ?? 'Không có lịch chờ duyệt', detail: pendingSchedule ? `Tạo ngày ${pendingSchedule.createdAt.slice(0, 10)}.` : '', priority: 'Cao', status: 'Chờ duyệt', icon: CalendarCheck2, path: '/manager/schedules?status=PENDING_APPROVAL' },
    { title: 'Ngày nghỉ chờ duyệt', related: pendingLeave?.employeeName ?? 'Không có yêu cầu', detail: pendingLeave ? `Yêu cầu từ ${pendingLeave.startDate} đến ${pendingLeave.endDate}.` : '', priority: 'Cao', status: 'Chờ duyệt', icon: Clock3, path: '/manager/leave-requests?status=PENDING' },
    { title: 'Đổi ca chờ xử lý', related: pendingSwap?.employeeName ?? 'Không có yêu cầu', detail: pendingSwap?.reason ?? '', priority: 'Trung bình', status: 'Chờ xử lý', icon: ArrowRight, path: '/manager/swap-requests?status=PENDING' },
    { title: 'Nguyện vọng chưa xem', related: pendingPreference?.employeeName ?? 'Không có nguyện vọng', detail: pendingPreference?.note ?? '', priority: 'Trung bình', status: 'Chưa xem', icon: Users, path: '/manager/preferences?status=PENDING' },
    { title: 'Cảnh báo lịch nghiêm trọng', related: criticalWarning?.employeeName ?? 'Không có cảnh báo', detail: criticalWarning?.content ?? '', priority: 'Cao', status: 'Cần xem', icon: AlertTriangle, path: '/manager/schedules/schedule-demo-01?tab=warnings' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm text-slate-500">Xin chào,</p>
          <h2 className="text-2xl font-semibold text-slate-900">{user?.name ?? 'Quản lý'}</h2>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-sm text-blue-700">
          <CalendarCheck2 className="h-4 w-4" />
          {currentDateLabel}
        </div>
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {managerDashboardData.summary.map((item, index) => (
          <Link key={item.label} to={summaryLinks[index]} className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
          <Card className="h-full cursor-pointer border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
            <CardHeader className="p-4 pb-3">
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="text-sm font-medium text-slate-500">{item.label}</CardTitle>
                <Badge variant="secondary" className="text-[10px] font-medium">
                  {item.trend}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="px-4 pb-4">
              <div className="text-3xl font-bold text-slate-900">{item.value}</div>
            </CardContent>
          </Card>
          </Link>
        ))}
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Lịch ca trực gần nhất</CardTitle>
            <CardDescription>Danh sách ca điều phối trong tuần hiện tại.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {[{ label: 'Thứ 2 - 17/09', value: 'Ca sáng: 12 nhân viên', status: 'Ổn định', path: '/manager/schedules/schedule-demo-01' }, { label: 'Thứ 3 - 18/09', value: 'Ca chiều: 9 nhân viên', status: 'Thiếu 2', path: '/manager/schedules/schedule-demo-01?tab=warnings' }, { label: 'Thứ 4 - 19/09', value: 'Ca đêm: 11 nhân viên', status: 'Ổn định', path: '/manager/schedules/schedule-demo-01' }].map((item) => (
              <Link key={item.label} to={item.path} className="flex cursor-pointer items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                <div>
                  <p className="font-medium text-slate-800">{item.label}</p>
                  <p className="text-sm text-slate-500">{item.value}</p>
                </div>
                <Badge variant={item.status === 'Thiếu 2' ? 'destructive' : 'secondary'}>{item.status}</Badge>
              </Link>
            ))}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Các thao tác nhanh</CardTitle>
            <CardDescription>Điều hướng nhanh tới chức năng trọng tâm.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {quickActions.map(({ label, icon: Icon, path }) => (
              <Button key={label} variant="outline" className="w-full cursor-pointer justify-between gap-3 focus-visible:ring-2 focus-visible:ring-blue-500" onClick={() => navigate(path)}>
                <span className="flex items-center gap-2">
                  <Icon className="h-4 w-4" />
                  {label}
                </span>
                <ArrowRight className="h-4 w-4" />
              </Button>
            ))}
          </CardContent>
        </Card>
      </section>

      <section id="action-center" className="scroll-mt-6 space-y-4">
        <div className="flex items-end justify-between gap-3">
          <div><h2 className="text-xl font-semibold text-slate-900">Cần xử lý</h2><p className="text-sm text-slate-500">Các mục cần xem xét trước khi thực hiện quyết định.</p></div>
          <Button variant="outline" onClick={() => navigate('/manager/leave-requests?status=PENDING')}>Xem tất cả</Button>
        </div>
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardContent className="space-y-3 p-4">
            {actionItems.map(({ title, related, detail, priority, status, icon: Icon, path }) => (
              <Link key={title} to={path} className="flex cursor-pointer flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 md:flex-row md:items-center md:justify-between">
                <div className="flex min-w-0 items-start gap-3"><Icon className="mt-0.5 h-5 w-5 shrink-0 text-blue-600" /><div className="min-w-0"><p className="font-medium text-slate-900">{title}</p><p className="text-sm text-slate-600">{related}</p><p className="text-xs text-slate-500">{detail}</p></div></div>
                <div className="flex items-center gap-2 text-xs"><Badge variant={priority === 'Cao' ? 'destructive' : 'secondary'}>{priority}</Badge><Badge variant="outline">{status}</Badge><ArrowRight className="h-4 w-4 text-slate-500" /></div>
              </Link>
            ))}
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-6 xl:grid-cols-2">
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Yêu cầu đang chờ xử lý</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {managerDashboardData.requests.map((request) => (
              <div key={request.id} className="flex items-center justify-between rounded-xl border border-slate-200 p-3">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="font-medium text-slate-800">{request.employee}</p>
                    <Badge variant="secondary">{request.type}</Badge>
                  </div>
                  <p className="text-sm text-slate-500">{request.shift} • {request.date}</p>
                </div>
                <Button variant="outline" size="sm" onClick={() => navigate(request.type === 'Nghỉ phép' ? '/manager/leave-requests?status=PENDING' : '/manager/swap-requests?status=PENDING')}>Xem chi tiết</Button>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Cảnh báo lịch</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {managerDashboardData.alerts.map((alert) => (
              <Link key={alert.title} to="/manager/schedules/schedule-demo-01?tab=warnings" className="block cursor-pointer rounded-xl border border-slate-200 bg-slate-50 p-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-slate-800">
                    <AlertTriangle className="h-4 w-4 text-amber-500" />
                    <span className="font-medium">{alert.title}</span>
                  </div>
                  <Badge variant={alert.severity === 'Cao' ? 'destructive' : 'secondary'}>{alert.severity}</Badge>
                </div>
                <p className="mt-2 text-sm text-slate-600">{alert.detail}</p>
              </Link>
            ))}
          </CardContent>
        </Card>
      </section>
    </div>
  )
}

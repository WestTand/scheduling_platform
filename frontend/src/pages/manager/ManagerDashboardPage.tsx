import { AlertTriangle, ArrowRight, CalendarCheck2, CheckCircle2, Sparkles, Users } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { currentDateLabel, managerDashboardData } from '@/mocks/dashboardData'
import { useAuth } from '@/contexts/useAuth'

export function ManagerDashboardPage() {
  const { user } = useAuth()

  const quickActions = [
    { label: 'Tạo lịch tự động', icon: Sparkles },
    { label: 'Xem lịch', icon: CalendarCheck2 },
    { label: 'Quản lý nhân viên', icon: Users },
    { label: 'Duyệt yêu cầu', icon: CheckCircle2 },
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
        {managerDashboardData.summary.map((item) => (
          <Card key={item.label} className="border-slate-200 bg-white shadow-sm">
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
        ))}
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Lịch ca trực gần nhất</CardTitle>
            <CardDescription>Danh sách ca điều phối trong tuần hiện tại.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {[{ label: 'Thứ 2 - 17/09', value: 'Ca sáng: 12 nhân viên', status: 'Ổn định' }, { label: 'Thứ 3 - 18/09', value: 'Ca chiều: 9 nhân viên', status: 'Thiếu 2' }, { label: 'Thứ 4 - 19/09', value: 'Ca đêm: 11 nhân viên', status: 'Ổn định' }].map((item) => (
              <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                <div>
                  <p className="font-medium text-slate-800">{item.label}</p>
                  <p className="text-sm text-slate-500">{item.value}</p>
                </div>
                <Badge variant={item.status === 'Thiếu 2' ? 'destructive' : 'secondary'}>{item.status}</Badge>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Các thao tác nhanh</CardTitle>
            <CardDescription>Điều hướng nhanh tới chức năng trọng tâm.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {quickActions.map(({ label, icon: Icon }) => (
              <Button key={label} variant="outline" className="w-full justify-between gap-3" onClick={() => window.alert(`${label} đang trong giai đoạn phát triển.`)}>
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
                <Button variant="outline" size="sm" onClick={() => window.alert('Duyệt yêu cầu đang được mock cho giao diện.')}>Xem</Button>
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
              <div key={alert.title} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-slate-800">
                    <AlertTriangle className="h-4 w-4 text-amber-500" />
                    <span className="font-medium">{alert.title}</span>
                  </div>
                  <Badge variant={alert.severity === 'Cao' ? 'destructive' : 'secondary'}>{alert.severity}</Badge>
                </div>
                <p className="mt-2 text-sm text-slate-600">{alert.detail}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      </section>
    </div>
  )
}

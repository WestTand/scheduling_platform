import { BellDot, CalendarCheck2, Clock3, FileText, PlaneTakeoff } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { employeeDashboardData } from '@/mocks/dashboardData'
import { useAuth } from '@/contexts/useAuth'
import { formatShiftRangeLabel, getShiftDefinition } from '@/configs/shiftDefinitions'

export function EmployeeDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const quickActions = [
    { label: 'Xem lịch của tôi', icon: CalendarCheck2, path: '/employee/schedule' },
    { label: 'Đăng ký ngày nghỉ', icon: PlaneTakeoff, path: '/employee/leave-requests?action=create' },
    { label: 'Đăng ký nguyện vọng', icon: FileText, path: '/employee/preferences?action=create' },
    { label: 'Gửi yêu cầu đổi ca', icon: Clock3, path: '/employee/swap-requests?action=create' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm text-slate-500">Xin chào,</p>
          <h2 className="text-2xl font-semibold text-slate-900">{user?.name ?? 'Nhân viên'}</h2>
        </div>
        <Badge variant="secondary" className="w-fit">Tuần 38 • 16/09/2026</Badge>
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {employeeDashboardData.summary.map((item, index) => {
          const paths = ['/employee/schedule?view=today', '/employee/schedule?assignmentId=assignment-05', '/employee/schedule?view=week', '/employee/leave-requests?status=PENDING']
          const shift = 'shiftCode' in item ? getShiftDefinition(item.shiftCode ?? '') : undefined
          return <Link key={item.label} to={paths[index]} className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
          <Card className="h-full cursor-pointer border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
            <CardHeader className="px-4 pb-3 pt-4">
              <CardTitle className="text-sm text-slate-500">{item.label}</CardTitle>
            </CardHeader>
            <CardContent className="px-4 pb-4">
              <div className="text-2xl font-bold text-slate-900">{shift?.name ?? item.value}</div>
              <p className="mt-1 text-xs text-slate-500">{shift ? formatShiftRangeLabel(shift) : item.detail}</p>
            </CardContent>
          </Card>
          </Link>
        })}
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Lịch làm việc trong tuần</CardTitle>
            <CardDescription>Ca trực sắp tới của bạn.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {employeeDashboardData.nextShifts.map((shift, index) => (
              <Link key={shift.date} to={`/employee/schedule?assignmentId=assignment-0${index + 4}`} className="flex cursor-pointer items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                <div>
                  <p className="font-medium text-slate-800">{shift.day} • {shift.date}</p>
                  <p className="text-sm text-slate-500">{shift.shiftCodes ? shift.shiftCodes.map((code) => getShiftDefinition(code)?.name).join(' · ') : `${getShiftDefinition(shift.shiftCode)?.name} • ${formatShiftRangeLabel(getShiftDefinition(shift.shiftCode)!)}`}</p>
                </div>
                <Badge variant="outline">Đã xác nhận</Badge>
              </Link>
            ))}
            <Button variant="outline" className="w-full" onClick={() => navigate('/employee/schedule?view=week')}>Xem toàn bộ lịch</Button>
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Thao tác nhanh</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {quickActions.map(({ label, icon: Icon, path }) => (
              <Button key={label} variant="outline" className="w-full cursor-pointer justify-start gap-2 focus-visible:ring-2 focus-visible:ring-slate-500" onClick={() => navigate(path)}>
                <Icon className="h-4 w-4" />
                {label}
              </Button>
            ))}
          </CardContent>
        </Card>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardHeader className="px-5 pb-3 pt-5">
          <CardTitle>Thông báo mới</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 px-5 pb-5">
            {employeeDashboardData.notifications.map((notice, index) => (
            <Link key={notice} to={`/employee/notifications?notificationId=NOTI-00${index + 1}`} className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
              <div className="mt-0.5 flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-700">
                <BellDot className="h-4 w-4" />
              </div>
              <p className="text-sm text-slate-700">{notice}</p>
            </Link>
          ))}
            <Link to="/employee/notifications" className="inline-flex text-sm font-medium text-blue-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">Xem tất cả thông báo</Link>
        </CardContent>
      </Card>
    </div>
  )
}

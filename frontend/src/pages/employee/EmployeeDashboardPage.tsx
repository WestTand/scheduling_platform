import { useEffect, useMemo, useState } from 'react'
import { BellDot, CalendarCheck2, Clock3, FileText, PlaneTakeoff } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { apiConfig } from '@/config/api'
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import { useAuth } from '@/contexts/useAuth'
import { employeeScheduleService } from '@/services/employeeScheduleService'
import { navigationSummaryService } from '@/services/navigationSummaryService'
import { notificationService } from '@/services/notificationService'
import { adaptBackendEmployeeSchedule, getEmployeeWeekWindow } from './EmployeeSchedulePage.helpers'
import { getEmployeeSummaryCards, pickNextShiftDays } from './EmployeeDashboard.helpers'
import type { NavigationSummary } from '@/types/navigationSummary'
import type { NotificationItem } from '@/types/management'
import type { EmployeeScheduleViewItem } from './EmployeeSchedulePage.helpers'

export function EmployeeDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const week = useMemo(() => getEmployeeWeekWindow(0), [])
  const todayInput = useMemo(() => {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  }, [])
  const [isLoading, setIsLoading] = useState(() => !apiConfig.useMockApi)
  const [loadError, setLoadError] = useState('')
  const [scheduleItems, setScheduleItems] = useState<EmployeeScheduleViewItem[]>([])
  const [summary, setSummary] = useState<NavigationSummary | null>(null)
  const [notices, setNotices] = useState<NotificationItem[]>([])

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    const load = async () => {
      try {
        const [rawSchedule, navSummary, noticePage] = await Promise.all([
          employeeScheduleService.list(week),
          navigationSummaryService.get(),
          notificationService.list({ page: 1, pageSize: 3 }),
        ])
        if (ignore) return
        setScheduleItems(adaptBackendEmployeeSchedule(rawSchedule))
        setSummary(navSummary)
        setNotices(noticePage.data)
        setLoadError('')
      } catch (loadErr) {
        if (!ignore) setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải dữ liệu tổng quan.')
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }
    void load()
    return () => { ignore = true }
  }, [week])

  const todayShiftName = scheduleItems.find((item) => item.date === todayInput)?.shiftName ?? null
  const nextShiftItem = scheduleItems.find((item) => item.date > todayInput) ?? null
  const nextShiftName = nextShiftItem?.shiftName ?? null
  const totalMinutes = scheduleItems.reduce((total, item) => total + (getShiftDefinition(item.shiftCode)?.workMinutes ?? 0), 0)
  const summaryCards = getEmployeeSummaryCards({
    todayShiftName,
    nextShiftName,
    totalMinutes,
    pendingLeave: summary?.pendingLeaveRequests ?? 0,
    pendingPreference: summary?.pendingPreferences ?? 0,
    pendingSwap: summary?.pendingSwapRequests ?? 0,
  })
  const nextShiftDays = pickNextShiftDays(scheduleItems.filter((item) => item.date >= todayInput), 3)

  const summaryPaths = [
    '/employee/schedule?view=today',
    nextShiftItem ? `/employee/schedule?assignmentId=${nextShiftItem.id}` : '/employee/schedule',
    '/employee/schedule?view=week',
    '/employee/leave-requests?status=PENDING',
  ]

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
        <Badge variant="secondary" className="w-fit">{week.from} - {week.to}</Badge>
      </div>

      {!apiConfig.useMockApi && isLoading && (
        <p className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">Đang tải dữ liệu tổng quan...</p>
      )}
      {!apiConfig.useMockApi && !isLoading && loadError && (
        <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</p>
      )}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summaryCards.map((item, index) => (
          <Link key={item.label} to={summaryPaths[index]} className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
          <Card className="h-full cursor-pointer border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
            <CardHeader className="px-4 pb-3 pt-4">
              <CardTitle className="text-sm text-slate-500">{item.label}</CardTitle>
            </CardHeader>
            <CardContent className="px-4 pb-4">
              <div className="text-2xl font-bold text-slate-900">{item.value}</div>
              <p className="mt-1 text-xs text-slate-500">{item.detail}</p>
            </CardContent>
          </Card>
          </Link>
        ))}
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Lịch làm việc trong tuần</CardTitle>
            <CardDescription>Ca trực sắp tới của bạn.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {nextShiftDays.length === 0 ? (
              <p className="text-sm text-slate-500">Bạn chưa có lịch làm việc trong tuần này.</p>
            ) : (
              nextShiftDays.map((shift) => (
                <Link key={shift.id} to={`/employee/schedule?assignmentId=${shift.id}`} className="flex cursor-pointer items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                  <div>
                    <p className="font-medium text-slate-800">{shift.day} • {shift.date}</p>
                    <p className="text-sm text-slate-500">{shift.shiftName} • {shift.timeRange}</p>
                  </div>
                  <Badge variant="outline">{shift.status}</Badge>
                </Link>
              ))
            )}
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
          {notices.length === 0 ? (
            <p className="text-sm text-slate-500">Không có thông báo mới.</p>
          ) : (
            notices.map((notice) => (
              <Link key={notice.id} to={`/employee/notifications?notificationId=${notice.id}`} className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                <div className="mt-0.5 flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-700">
                  <BellDot className="h-4 w-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-800">{notice.title}</p>
                  <p className="text-sm text-slate-700">{notice.content}</p>
                </div>
              </Link>
            ))
          )}
            <Link to="/employee/notifications" className="inline-flex text-sm font-medium text-blue-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">Xem tất cả thông báo</Link>
        </CardContent>
      </Card>
    </div>
  )
}

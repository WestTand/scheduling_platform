import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, ArrowRight, CalendarCheck2, CheckCircle2, Clock3, FileWarning, Sparkles, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { apiConfig } from '@/config/api'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import { backendScheduleService } from '@/services/backendScheduleService'
import { leaveRequestService } from '@/services/leaveRequestService'
import { navigationSummaryService } from '@/services/navigationSummaryService'
import { preferenceService } from '@/services/preferenceService'
import { swapRequestService } from '@/services/swapRequestService'
import { getManagerActionItems, getManagerSummaryCards, pickScheduleAlerts, pickUpcomingShifts } from './ManagerDashboard.helpers'
import { getEmployeeWeekWindow } from '@/pages/employee/EmployeeSchedulePage.helpers'
import type { LeaveRequest, SwapRequest, WorkPreference } from '@/types/management'
import type { NavigationSummary } from '@/types/navigationSummary'
import type { ScheduleDetail, WorkSchedule } from '@/types/schedule'
import { useAuth } from '@/contexts/useAuth'

export function ManagerDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const week = useMemo(() => getEmployeeWeekWindow(0), [])
  const [isLoading, setIsLoading] = useState(() => !apiConfig.useMockApi)
  const [loadError, setLoadError] = useState('')
  const [employeeTotal, setEmployeeTotal] = useState(0)
  const [periods, setPeriods] = useState<WorkSchedule[]>([])
  const [details, setDetails] = useState<ScheduleDetail[]>([])
  const [summary, setSummary] = useState<NavigationSummary | null>(null)
  const [leaveHead, setLeaveHead] = useState<LeaveRequest | null>(null)
  const [swapHead, setSwapHead] = useState<SwapRequest | null>(null)
  const [preferenceHead, setPreferenceHead] = useState<WorkPreference | null>(null)

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    const load = async () => {
      try {
        const [employees, navSummary, weekPeriods, leaves, swaps, preferences] = await Promise.all([
          backendEmployeeService.list({ status: 'ACTIVE', page: 1, pageSize: 1 }),
          navigationSummaryService.get(),
          backendScheduleService.listPeriods({ status: 'ALL', startDate: week.from, endDate: week.to }),
          leaveRequestService.list({ status: 'PENDING', page: 1, pageSize: 1 }),
          swapRequestService.list({ status: 'PENDING_APPROVAL', page: 1, pageSize: 1 }),
          preferenceService.list({ status: 'PENDING', page: 1, pageSize: 1 }),
        ])
        const weekDetails = await Promise.all(weekPeriods.slice(0, 5).map((period) => backendScheduleService.getScheduleDetail(period.id)))
        if (ignore) return
        setEmployeeTotal(employees.total)
        setSummary(navSummary)
        setPeriods(weekPeriods)
        setDetails(weekDetails)
        setLeaveHead(leaves.data[0] ?? null)
        setSwapHead(swaps.data[0] ?? null)
        setPreferenceHead(preferences.data[0] ?? null)
        setLoadError('')
      } catch (loadErr) {
        if (!ignore) setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải dữ liệu tổng quan.')
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }
    void load()
    return () => { ignore = true }
  }, [week.from, week.to])

  const weekShiftCount = useMemo(() => periods.reduce((total, period) => total + period.shiftCount, 0), [periods])
  const draftDetails = useMemo(() => details.filter((detail) => detail.status === 'DRAFT'), [details])
  const warningCount = useMemo(() => draftDetails.reduce((total, detail) => total + detail.warnings.length, 0), [draftDetails])
  const summaryCards = useMemo(() => getManagerSummaryCards({
    employeeTotal,
    weekShiftCount,
    pendingLeave: summary?.pendingLeaveRequests ?? 0,
    pendingSwap: summary?.pendingSwapRequests ?? 0,
    pendingPreference: summary?.pendingPreferences ?? 0,
    warningCount,
  }), [employeeTotal, weekShiftCount, summary, warningCount])
  const upcomingShifts = useMemo(() => pickUpcomingShifts(details, 3), [details])
  const alerts = useMemo(() => pickScheduleAlerts(details, 3), [details])
  const draftPeriod = useMemo(() => {
    const first = periods.find((period) => period.status === 'DRAFT')
    return first ? { id: first.id, name: first.name } : null
  }, [periods])
  const criticalWarning = useMemo(() => {
    const high = draftDetails.flatMap((detail) => detail.warnings.map((warning) => ({ ...warning, periodId: detail.id }))).find((warning) => warning.severity === 'HIGH')
    return high ? { periodId: high.periodId, employeeName: high.employeeName, content: high.content } : null
  }, [draftDetails])
  const actionItems = useMemo(() => getManagerActionItems({ draftPeriod, leave: leaveHead, swap: swapHead, preference: preferenceHead, criticalWarning }), [draftPeriod, leaveHead, swapHead, preferenceHead, criticalWarning])

  const todayLabel = new Date().toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })

  const quickActions = [
    { label: 'Tạo lịch tự động', icon: Sparkles, path: '/manager/schedule-generation' },
    { label: 'Xem lịch làm việc', icon: CalendarCheck2, path: '/manager/schedules' },
    { label: 'Xử lý yêu cầu nghỉ', icon: CheckCircle2, path: '/manager/leave-requests?status=PENDING' },
    { label: 'Xử lý đổi ca', icon: Clock3, path: '/manager/swap-requests?status=PENDING_APPROVAL' },
    { label: 'Xuất dữ liệu', icon: FileWarning, path: '/manager/exports' },
  ]

  const actionIcons = [CalendarCheck2, Clock3, ArrowRight, Users, AlertTriangle]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm text-slate-500">Xin chào,</p>
          <h2 className="text-2xl font-semibold text-slate-900">{user?.name ?? 'Quản lý'}</h2>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-sm text-blue-700">
          <CalendarCheck2 className="h-4 w-4" />
          {todayLabel}
        </div>
      </div>

      {!apiConfig.useMockApi && isLoading && (
        <p className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">Đang tải dữ liệu tổng quan...</p>
      )}
      {!apiConfig.useMockApi && !isLoading && loadError && (
        <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</p>
      )}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summaryCards.map((item) => (
          <Link key={item.label} to={item.path} className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
          <Card className="h-full cursor-pointer border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
            <CardHeader className="p-4 pb-3">
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="text-sm font-medium text-slate-500">{item.label}</CardTitle>
                <Badge variant="secondary" className="text-[10px] font-medium">
                  {item.context}
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
            {upcomingShifts.length === 0 ? (
              <p className="text-sm text-slate-500">Chưa có ca trực nào trong tuần này.</p>
            ) : (
              upcomingShifts.map((row) => (
                <Link key={row.key} to={row.path} className="flex cursor-pointer items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                  <div>
                    <p className="font-medium text-slate-800">{row.dateLabel}</p>
                    <p className="text-sm text-slate-500">{row.shiftName} · {row.unassigned ? 'Chưa phân công' : `${row.assignedCount} phân công`}</p>
                  </div>
                  <Badge variant={row.hasWarning || row.unassigned ? 'destructive' : 'secondary'}>{row.hasWarning || row.unassigned ? 'Cần xem' : 'Ổn định'}</Badge>
                </Link>
              ))
            )}
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
            {actionItems.map(({ title, related, detail, priority, status, path }, index) => {
              const Icon = actionIcons[index] ?? AlertTriangle
              return (
                <Link key={title} to={path} className="flex cursor-pointer flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 md:flex-row md:items-center md:justify-between">
                  <div className="flex min-w-0 items-start gap-3"><Icon className="mt-0.5 h-5 w-5 shrink-0 text-blue-600" /><div className="min-w-0"><p className="font-medium text-slate-900">{title}</p><p className="text-sm text-slate-600">{related}</p><p className="text-xs text-slate-500">{detail}</p></div></div>
                  <div className="flex items-center gap-2 text-xs"><Badge variant={priority === 'Cao' ? 'destructive' : 'secondary'}>{priority}</Badge><Badge variant="outline">{status}</Badge><ArrowRight className="h-4 w-4 text-slate-500" /></div>
                </Link>
              )
            })}
          </CardContent>
        </Card>
      </section>

      <section>
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="px-5 pb-3 pt-5">
            <CardTitle>Cảnh báo lịch</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 px-5 pb-5">
            {alerts.length === 0 ? (
              <p className="text-sm text-slate-500">Không có cảnh báo lịch.</p>
            ) : (
              alerts.map((alert) => (
                <Link key={alert.id} to={alert.path} className="block cursor-pointer rounded-xl border border-slate-200 bg-slate-50 p-3 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 text-slate-800">
                      <AlertTriangle className="h-4 w-4 text-amber-500" />
                      <span className="font-medium">{alert.title}</span>
                    </div>
                    <Badge variant={alert.severity === 'Cao' ? 'destructive' : 'secondary'}>{alert.severity}</Badge>
                  </div>
                  <p className="mt-2 text-sm text-slate-600">{alert.detail}</p>
                </Link>
              ))
            )}
          </CardContent>
        </Card>
      </section>
    </div>
  )
}

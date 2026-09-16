import type { ScheduleAssignment, ScheduleViewMode } from '@/types/schedule'

import { ScheduleAssignmentCard } from './ScheduleAssignmentCard'
import { ScheduleDayList } from './ScheduleDayList'

interface ScheduleCalendarProps {
  assignments: ScheduleAssignment[]
  mode: ScheduleViewMode
  employeeFilter: string
  shiftFilter: string
  dateFilter: string
  warningOnly: boolean
}

export function ScheduleCalendar({ assignments, mode, employeeFilter, shiftFilter, dateFilter, warningOnly }: ScheduleCalendarProps) {
  const filtered = assignments.filter((assignment) => {
    const matchesEmployee = !employeeFilter || assignment.employeeNames.includes(employeeFilter)
    const matchesShift = !shiftFilter || assignment.shiftCode === shiftFilter
    const matchesDate = !dateFilter || assignment.date === dateFilter
    const matchesWarning = !warningOnly || assignment.warningCount > 0
    return matchesEmployee && matchesShift && matchesDate && matchesWarning
  })

  const grouped = filtered.reduce<Record<string, ScheduleAssignment[]>>((result, assignment) => {
    result[assignment.date] = [...(result[assignment.date] ?? []), assignment]
    return result
  }, {})

  if (!filtered.length) {
    return <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-sm text-slate-500">Không có phân công mẫu phù hợp với bộ lọc.</div>
  }

  if (mode === 'LIST') {
    return (
      <div className="space-y-4">
        {filtered.map((assignment) => (
          <div key={assignment.id} className="grid gap-3 md:grid-cols-[140px_1fr]">
            <div className="rounded-xl bg-slate-50 p-3 text-sm font-medium text-slate-700">{new Date(assignment.date).toLocaleDateString('vi-VN')}</div>
            <ScheduleAssignmentCard assignment={assignment} />
          </div>
        ))}
      </div>
    )
  }

  if (mode === 'DAY') {
    return <ScheduleDayList assignments={filtered} />
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Object.entries(grouped).map(([date, dayAssignments]) => (
        <section key={date} className="min-w-0 rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <h3 className="mb-3 font-semibold text-slate-900">{new Date(date).toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })}</h3>
          <div className="space-y-3">{dayAssignments.map((assignment) => <ScheduleAssignmentCard key={assignment.id} assignment={assignment} />)}</div>
        </section>
      ))}
    </div>
  )
}

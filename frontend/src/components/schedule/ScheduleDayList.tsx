import type { ScheduleAssignment } from '@/types/schedule'

import { ScheduleAssignmentCard } from './ScheduleAssignmentCard'

interface ScheduleDayListProps {
  assignments: ScheduleAssignment[]
}

export function ScheduleDayList({ assignments }: ScheduleDayListProps) {
  const grouped = assignments.reduce<Record<string, ScheduleAssignment[]>>((result, assignment) => {
    result[assignment.date] = [...(result[assignment.date] ?? []), assignment]
    return result
  }, {})

  return (
    <div className="space-y-4">
      {Object.entries(grouped).map(([date, dayAssignments]) => (
        <section key={date} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <h3 className="mb-3 font-semibold text-slate-900">{new Date(date).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })}</h3>
          <div className="grid gap-3 md:grid-cols-2">{dayAssignments.map((assignment) => <ScheduleAssignmentCard key={assignment.id} assignment={assignment} />)}</div>
        </section>
      ))}
    </div>
  )
}

import { AlertTriangle, Users } from 'lucide-react'

import { formatShiftRangeLabel, getShiftDefinition } from '@/configs/shiftDefinitions'
import type { ScheduleAssignment } from '@/types/schedule'

interface ScheduleAssignmentCardProps {
  assignment: ScheduleAssignment
}

export function ScheduleAssignmentCard({ assignment }: ScheduleAssignmentCardProps) {
  const shift = getShiftDefinition(assignment.shiftCode)

  if (!shift) {
    return null
  }

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm" style={{ borderLeftColor: shift.color, borderLeftWidth: 4 }}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-slate-900">{shift.name}</p>
          <p className="text-xs text-slate-500">{assignment.shiftCode} · {formatShiftRangeLabel(shift)}</p>
        </div>
        {assignment.warningCount > 0 && <AlertTriangle className="h-4 w-4 text-amber-500" aria-label="Có cảnh báo" />}
      </div>
      <div className="mt-3 space-y-1 text-sm text-slate-600">
        {assignment.employeeNames.map((employeeName) => (
          <div key={employeeName} className="flex items-center gap-2">
            <Users className="h-3.5 w-3.5 text-slate-400" />
            <span>{employeeName}</span>
          </div>
        ))}
      </div>
    </article>
  )
}

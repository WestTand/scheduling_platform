import { AlertTriangle, Info, ShieldAlert } from 'lucide-react'

import { getShiftDefinition } from '@/configs/shiftDefinitions'
import type { ScheduleWarning } from '@/types/schedule'

interface ScheduleWarningListProps {
  warnings: ScheduleWarning[]
}

const typeLabels = {
  HARD_CONSTRAINT: 'Ràng buộc cứng',
  SOFT_CONSTRAINT: 'Ràng buộc mềm',
  INFO: 'Thông tin',
}

const styles = {
  HARD_CONSTRAINT: 'border-red-200 bg-red-50',
  SOFT_CONSTRAINT: 'border-amber-200 bg-amber-50',
  INFO: 'border-blue-200 bg-blue-50',
}

export function ScheduleWarningList({ warnings }: ScheduleWarningListProps) {
  return (
    <div className="space-y-3">
      {warnings.map((warning) => {
        const Icon = warning.type === 'HARD_CONSTRAINT' ? ShieldAlert : warning.type === 'SOFT_CONSTRAINT' ? AlertTriangle : Info
        return (
          <article key={warning.id} className={`rounded-xl border p-4 ${styles[warning.type]}`}>
            <div className="flex gap-3">
              <Icon className="mt-0.5 h-5 w-5 shrink-0 text-slate-700" />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-slate-900">{typeLabels[warning.type]}</span>
                  <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs text-slate-600">Mức độ: {warning.severity}</span>
                </div>
                <p className="mt-2 text-sm text-slate-700">{warning.content}</p>
                <p className="mt-2 text-xs text-slate-600">{warning.employeeName} · {warning.date} · {getShiftDefinition(warning.shiftCode)?.name ?? 'Lịch tổng hợp'}</p>
                <p className="mt-2 text-sm text-slate-600"><strong>Gợi ý:</strong> {warning.suggestion}</p>
              </div>
            </div>
          </article>
        )
      })}
    </div>
  )
}

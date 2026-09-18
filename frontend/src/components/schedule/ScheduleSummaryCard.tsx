import type { ReactNode } from 'react'

import { Card, CardContent } from '@/components/ui/card'

interface ScheduleSummaryCardProps {
  label: string
  value: string | number
  icon: ReactNode
  tone: 'blue' | 'green' | 'amber' | 'red'
}

const toneClasses = {
  blue: 'bg-blue-50 text-blue-700',
  green: 'bg-emerald-50 text-emerald-700',
  amber: 'bg-amber-50 text-amber-700',
  red: 'bg-red-50 text-red-700',
}

export function ScheduleSummaryCard({ label, value, icon, tone }: ScheduleSummaryCardProps) {
  return (
    <Card className="border-slate-200 shadow-sm">
      <CardContent className="flex items-center justify-between p-4">
        <div>
          <p className="text-sm text-slate-500">{label}</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
        </div>
        <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${toneClasses[tone]}`}>{icon}</div>
      </CardContent>
    </Card>
  )
}

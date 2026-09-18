import type { ReactNode } from 'react'

import { Card, CardContent } from '@/components/ui/card'

interface StatCardProps {
  label: string
  value: string | number
  icon?: ReactNode
  tone?: 'blue' | 'green' | 'amber' | 'red' | 'slate'
}

const toneMap = {
  blue: 'bg-blue-50 text-blue-700',
  green: 'bg-emerald-50 text-emerald-700',
  amber: 'bg-amber-50 text-amber-700',
  red: 'bg-rose-50 text-rose-700',
  slate: 'bg-slate-100 text-slate-700',
}

export function StatCard({ label, value, icon, tone = 'blue' }: StatCardProps) {
  return (
    <Card className="border-slate-200 bg-white shadow-sm">
      <CardContent className="flex items-center justify-between gap-3 px-4 py-4">
        <div>
          <p className="text-sm text-slate-500">{label}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{value}</p>
        </div>
        {icon && <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${toneMap[tone]}`}>{icon}</div>}
      </CardContent>
    </Card>
  )
}

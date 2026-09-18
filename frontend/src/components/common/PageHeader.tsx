import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'

interface PageHeaderProps {
  title: string
  description?: string
  action?: ReactNode
  actionLabel?: string
  onAction?: () => void
}

export function PageHeader({ title, description, action, actionLabel, onAction }: PageHeaderProps) {
  return (
    <div className="mb-6 flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center md:justify-between">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {action ? (
        <div>{action}</div>
      ) : onAction ? (
        <Button onClick={onAction}>{actionLabel ?? 'Thêm mới'}</Button>
      ) : null}
    </div>
  )
}

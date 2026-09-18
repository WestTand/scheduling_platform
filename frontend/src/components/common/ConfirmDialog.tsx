import { AlertTriangle } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  cancelLabel?: string
  confirmLabel?: string
  onCancel: () => void
  onConfirm: () => void
  icon?: ReactNode
  children?: ReactNode
}

export function ConfirmDialog({
  open,
  title,
  description,
  cancelLabel = 'Hủy',
  confirmLabel = 'Xác nhận',
  onCancel,
  onConfirm,
  icon = <AlertTriangle className="h-5 w-5 text-amber-600" />,
  children,
}: ConfirmDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-100">{icon}</div>
          <div>
            <h3 className="text-lg font-semibold text-slate-900">{title}</h3>
          </div>
        </div>

        <p className="text-sm leading-6 text-slate-600">{description}</p>

        {children}

        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button type="button" variant="destructive" onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}

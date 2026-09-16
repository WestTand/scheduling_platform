import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'

interface FormDialogProps {
  open: boolean
  title: string
  description?: string
  onClose: () => void
  onSubmit: () => void
  submitLabel?: string
  children: ReactNode
}

export function FormDialog({ open, title, description, onClose, onSubmit, submitLabel = 'Lưu', children }: FormDialogProps) {
  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4 backdrop-blur-sm">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border bg-white p-6 shadow-2xl">
        <div className="mb-4">
          <h3 className="text-xl font-semibold text-slate-900">{title}</h3>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
        </div>

        <div>{children}</div>

        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={onClose}>
            Hủy
          </Button>
          <Button type="button" onClick={onSubmit}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}

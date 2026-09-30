import { useMemo, useState, type PropsWithChildren } from 'react'
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { AppDialogContext, type AppDialogContextValue } from './AppDialogContext'
import { getAppDialogConfig, type AppDialogOptions, type AppDialogVariant } from './AppDialogProvider.helpers'

const ICONS: Record<AppDialogVariant, typeof Info> = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  error: XCircle,
}

export function AppDialogProvider({ children }: PropsWithChildren) {
  const [dialogOptions, setDialogOptions] = useState<AppDialogOptions | null>(null)
  const variant = dialogOptions?.variant ?? 'info'
  const dialogConfig = dialogOptions ? getAppDialogConfig(dialogOptions) : null
  const Icon = ICONS[variant]

  const value = useMemo<AppDialogContextValue>(() => ({
    showDialog: (options) => setDialogOptions(options),
  }), [])

  return (
    <AppDialogContext.Provider value={value}>
      {children}
      <Dialog open={Boolean(dialogConfig)} onOpenChange={(open) => { if (!open) setDialogOptions(null) }}>
        <DialogContent showCloseButton={false}>
          {dialogConfig && (
            <>
              <DialogHeader>
                <div className={cn('mb-1 flex size-10 items-center justify-center rounded-full', dialogConfig.iconClassName)}>
                  <Icon />
                </div>
                <DialogTitle>{dialogConfig.title}</DialogTitle>
                <DialogDescription>{dialogConfig.description}</DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button type="button" onClick={() => setDialogOptions(null)}>{dialogConfig.confirmLabel}</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </AppDialogContext.Provider>
  )
}

import { createContext, useContext } from 'react'

import type { AppDialogOptions } from './AppDialogProvider.helpers'

export interface AppDialogContextValue {
  showDialog: (options: AppDialogOptions) => void
}

export const AppDialogContext = createContext<AppDialogContextValue | undefined>(undefined)

export function useAppDialog() {
  const context = useContext(AppDialogContext)
  if (!context) {
    throw new Error('useAppDialog must be used within AppDialogProvider')
  }
  return context
}

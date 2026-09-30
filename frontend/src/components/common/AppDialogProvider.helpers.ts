export type AppDialogVariant = 'info' | 'success' | 'warning' | 'error'

export interface AppDialogOptions {
  title?: string
  description: string
  confirmLabel?: string
  variant?: AppDialogVariant
}

export interface AppDialogConfig {
  title: string
  description: string
  confirmLabel: string
  iconClassName: string
}

const DEFAULT_TITLES: Record<AppDialogVariant, string> = {
  info: 'Thông báo',
  success: 'Thành công',
  warning: 'Cần kiểm tra',
  error: 'Không thể thực hiện',
}

const ICON_CLASSES: Record<AppDialogVariant, string> = {
  info: 'bg-muted text-muted-foreground',
  success: 'bg-primary/10 text-primary',
  warning: 'bg-accent text-accent-foreground',
  error: 'bg-destructive/10 text-destructive',
}

export const getAppDialogConfig = ({ title, description, confirmLabel = 'Đóng', variant = 'info' }: AppDialogOptions): AppDialogConfig => ({
  title: title?.trim() || DEFAULT_TITLES[variant],
  description: description.trim() || 'Đã xảy ra lỗi. Vui lòng thử lại.',
  confirmLabel,
  iconClassName: ICON_CLASSES[variant],
})

import { Badge } from '@/components/ui/badge'
import type { AccountStatus } from '@/types/account'

interface AccountStatusBadgeProps {
  status?: AccountStatus | 'NO_ACCOUNT'
}

export function AccountStatusBadge({ status }: AccountStatusBadgeProps) {
  if (!status || status === 'NO_ACCOUNT') {
    return <Badge variant="outline">Chưa có tài khoản</Badge>
  }

  const labelMap: Record<AccountStatus, string> = {
    PENDING_ACTIVATION: 'Chờ kích hoạt',
    FORCE_PASSWORD_CHANGE: 'Đổi mật khẩu',
    ACTIVE: 'Đã kích hoạt',
    INACTIVE: 'Tạm ngưng',
    LOCKED: 'Đã khóa',
    DISABLED: 'Đã vô hiệu hóa',
  }

  const variantMap: Record<AccountStatus, 'secondary' | 'default' | 'destructive' | 'outline'> = {
    PENDING_ACTIVATION: 'secondary',
    FORCE_PASSWORD_CHANGE: 'secondary',
    ACTIVE: 'default',
    INACTIVE: 'outline',
    LOCKED: 'destructive',
    DISABLED: 'outline',
  }

  return <Badge variant={variantMap[status]}>{labelMap[status]}</Badge>
}

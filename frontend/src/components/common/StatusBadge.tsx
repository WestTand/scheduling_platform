import { Badge } from '@/components/ui/badge'

interface StatusBadgeProps {
  status: string
  variant?: 'default' | 'secondary' | 'destructive' | 'outline'
}

const normalizeStatus = (status: string) => status.toUpperCase()

export function StatusBadge({ status, variant }: StatusBadgeProps) {
  const normalized = normalizeStatus(status)
  const display = {
    ACTIVE: 'Đang hoạt động',
    INACTIVE: 'Tạm ngưng',
    PENDING: 'Chờ duyệt',
    PENDING_TARGET: 'Chờ nhân viên phản hồi',
    PENDING_APPROVAL: 'Chờ quản lý duyệt',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Đã từ chối',
    REVIEWED: 'Đã xem',
    CANCELLED: 'Đã hủy',
  }[normalized] ?? status

  const badgeVariant = normalized === 'ACTIVE' || normalized === 'APPROVED' ? 'default' : normalized === 'PENDING' || normalized === 'PENDING_TARGET' || normalized === 'PENDING_APPROVAL' || normalized === 'REVIEWED' ? 'secondary' : normalized === 'REJECTED' || normalized === 'INACTIVE' ? 'destructive' : 'outline'

  return <Badge variant={variant ?? badgeVariant}>{display}</Badge>
}

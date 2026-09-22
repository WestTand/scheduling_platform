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
    APPROVED: 'Đã duyệt',
    REJECTED: 'Đã từ chối',
    REVIEWED: 'Đã xem',
    CANCELLED: 'Đã hủy',
  }[normalized] ?? status

  const badgeVariant = normalized === 'ACTIVE' || normalized === 'APPROVED' ? 'default' : normalized === 'PENDING' || normalized === 'REVIEWED' ? 'secondary' : normalized === 'REJECTED' || normalized === 'INACTIVE' ? 'destructive' : 'outline'

  return <Badge variant={variant ?? badgeVariant}>{display}</Badge>
}

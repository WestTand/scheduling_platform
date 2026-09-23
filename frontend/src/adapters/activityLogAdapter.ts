import type { BackendActivityLogResponse } from '@/types/backendActivityLog'
import type { ActivityLog } from '@/types/management'

const entityLabels: Record<string, string> = {
  leave_requests: 'Đơn nghỉ phép',
  swap_requests: 'Yêu cầu đổi ca',
  preferences: 'Nguyện vọng làm việc',
  schedule_periods: 'Lịch làm việc',
  employees: 'Nhân viên',
  optimization_runs: 'Lượt tối ưu lịch',
}

const actionLabels: Record<string, string> = {
  APPROVE: 'Duyệt',
  REJECT: 'Từ chối',
  CREATE: 'Tạo mới',
  UPDATE: 'Cập nhật',
  LOCK: 'Khóa',
  UNLOCK: 'Mở khóa',
  PUBLISH: 'Công bố',
  OPTIMIZE: 'Tối ưu lịch',
}

const descriptions: Record<string, string> = {
  'APPROVE:leave_requests': 'Quản lý đã duyệt đơn nghỉ phép.',
  'REJECT:leave_requests': 'Quản lý đã từ chối đơn nghỉ phép.',
  'PUBLISH:schedule_periods': 'Quản lý đã công bố lịch làm việc.',
  'CREATE:employees': 'Quản lý đã tạo hồ sơ nhân viên.',
  'UPDATE:employees': 'Quản lý đã cập nhật hồ sơ nhân viên.',
}

const getEntityLabel = (entityType: string) => entityLabels[entityType] ?? entityType

const getTarget = ({ entityType, entityId }: Pick<BackendActivityLogResponse, 'entityType' | 'entityId'>) => {
  const label = getEntityLabel(entityType)
  return entityId == null ? label : `${label} #${entityId}`
}

const getDescription = ({ action, entityType }: Pick<BackendActivityLogResponse, 'action' | 'entityType'>) =>
  descriptions[`${action}:${entityType}`] ?? `Đã thực hiện thao tác ${actionLabels[action] ?? action} trên ${getEntityLabel(entityType)}.`

export const adaptBackendActivityLog = (response: BackendActivityLogResponse): ActivityLog => ({
  id: String(response.id),
  timestamp: response.createdAt,
  actorName: response.username,
  actorRole: 'MANAGER',
  action: actionLabels[response.action] ?? response.action,
  target: getTarget(response),
  description: getDescription(response),
  status: 'SUCCESS',
  ipAddress: response.ipAddress ?? '',
})

import type { BackendActivityLogResponse } from '@/types/backendActivityLog'
import type { ActivityLog } from '@/types/management'

export const adaptBackendActivityLog = (response: BackendActivityLogResponse): ActivityLog => ({
  id: String(response.id),
  timestamp: response.createdAt,
  actorName: response.username,
  actorRole: 'MANAGER',
  action: response.action,
  target: response.entityId == null ? response.entityType : `${response.entityType} #${response.entityId}`,
  description: response.newValueJson ?? response.oldValueJson ?? '',
  status: 'SUCCESS',
  ipAddress: response.ipAddress ?? '',
})

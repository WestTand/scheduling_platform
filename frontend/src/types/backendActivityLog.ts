export interface BackendActivityLogResponse {
  id: number
  accountId: number
  username: string
  action: string
  entityType: string
  entityId: number | null
  oldValueJson: string | null
  newValueJson: string | null
  createdAt: string
  ipAddress: string | null
}

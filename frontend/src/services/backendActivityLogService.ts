import { adaptBackendActivityLog } from '@/adapters/activityLogAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendActivityLogResponse } from '@/types/backendActivityLog'
import type { ActivityLog, PaginatedResult } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  action?: string
  startDate?: string
  endDate?: string
}

const paginate = (items: ActivityLog[], page: number, pageSize: number): PaginatedResult<ActivityLog> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

export const backendActivityLogService = {
  async list({ page = 1, pageSize = 5, search = '', action = 'ALL', startDate = '', endDate = '' }: ListParams = {}) {
    const params = new URLSearchParams()
    if (action !== 'ALL') params.set('action', action)
    if (startDate) params.set('from', startDate)
    if (endDate) params.set('to', endDate)
    const query = params.toString()
    const response = await requestJson<BackendActivityLogResponse[]>(`/api/admin/activity-logs${query ? `?${query}` : ''}`)
    const normalized = search.trim().toLowerCase()
    const items = response.map(adaptBackendActivityLog).filter((log) => {
      if (!normalized) return true
      return [log.actorName, log.target, log.description, log.action].some((value) => value.toLowerCase().includes(normalized))
    })
    return paginate(items, page, pageSize)
  },
}

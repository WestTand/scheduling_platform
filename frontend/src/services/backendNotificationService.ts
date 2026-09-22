import { adaptBackendNotification } from '@/adapters/notificationAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { BackendNotificationResponse } from '@/types/backendNotification'
import type { NotificationItem, PaginatedResult } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  type?: string
  status?: string
}

const paginate = (items: NotificationItem[], page: number, pageSize: number): PaginatedResult<NotificationItem> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterItems = (items: NotificationItem[], search: string, type: string) => {
  const normalized = search.trim().toLowerCase()
  return items.filter((item) => {
    const matchesSearch = !normalized || item.title.toLowerCase().includes(normalized) || item.content.toLowerCase().includes(normalized)
    const matchesType = type === 'ALL' || item.type === type
    return matchesSearch && matchesType
  })
}

export const backendNotificationService = {
  async list({ page = 1, pageSize = 5, search = '', type = 'ALL', status = 'ALL' }: ListParams = {}) {
    const params = new URLSearchParams()
    if (status === 'READ') params.set('read', 'true')
    if (status === 'UNREAD') params.set('read', 'false')
    const query = params.toString()
    const response = await requestJson<BackendNotificationResponse[]>(`/api/employee/notifications${query ? `?${query}` : ''}`)
    return paginate(filterItems(response.map(adaptBackendNotification), search, type), page, pageSize)
  },

  async markRead(id: string) {
    const response = await requestJson<BackendNotificationResponse>(`/api/employee/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' })
    return adaptBackendNotification(response)
  },

  async markAllRead() {
    await requestVoid('/api/employee/notifications/read-all', { method: 'PATCH' })
  },
}

import { apiConfig } from '@/config/api'
import { backendNotificationService } from '@/services/backendNotificationService'
import { notificationSeed } from '@/mocks/managementData'
import type { NotificationItem, NotificationPriority, NotificationType, PaginatedResult } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let notifications: NotificationItem[] = clone(notificationSeed)

export const notificationService = {
  async list({ page = 1, pageSize = 5, search = '', type = 'ALL', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; type?: string; status?: string }) {
    if (!apiConfig.useMockApi) {
      return backendNotificationService.list({ page, pageSize, search, type, status })
    }
    await delay(250)

    const filtered = notifications.filter((item) => {
      const matchesSearch = !search || item.title.toLowerCase().includes(search.toLowerCase()) || item.content.toLowerCase().includes(search.toLowerCase())
      const matchesType = type === 'ALL' || item.type === type
      const matchesStatus = status === 'ALL' || (status === 'READ' ? item.isRead : !item.isRead)

      return matchesSearch && matchesType && matchesStatus
    })

    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / pageSize))
    const safePage = Math.min(page, totalPages)
    const startIndex = (safePage - 1) * pageSize

    return {
      data: filtered.slice(startIndex, startIndex + pageSize),
      page: safePage,
      pageSize,
      total,
      totalPages,
    } satisfies PaginatedResult<NotificationItem>
  },

  async markRead(id: string) {
    if (!apiConfig.useMockApi) {
      return backendNotificationService.markRead(id)
    }
    await delay(150)
    const item = notifications.find((notification) => notification.id === id)

    if (!item) {
      throw new Error('Thông báo không tồn tại.')
    }

    item.isRead = true
    return item
  },

  async markAllRead() {
    if (!apiConfig.useMockApi) {
      await backendNotificationService.markAllRead()
      return []
    }
    await delay(200)
    notifications = notifications.map((item) => ({ ...item, isRead: true }))
    return notifications
  },

  async create(data: { title: string; content: string; type: NotificationType; priority: NotificationPriority; recipient: string; recipientType: 'ALL' | 'SELECTED'; createdAt?: string; isRead?: boolean; metadata?: string }) {
    if (!apiConfig.useMockApi) {
      throw new Error('Backend hiện chưa hỗ trợ tạo thông báo thủ công.')
    }
    await delay(200)

    const newItem: NotificationItem = {
      id: `NOTI-${Date.now()}`,
      title: data.title,
      content: data.content,
      type: data.type,
      priority: data.priority,
      recipient: data.recipient,
      recipientType: data.recipientType,
      createdAt: data.createdAt ?? new Date().toISOString(),
      isRead: data.isRead ?? false,
      metadata: data.metadata,
    }

    notifications = [newItem, ...notifications]
    return newItem
  },

  reset() {
    notifications = clone(notificationSeed)
  },
}

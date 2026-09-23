import type { BackendNotificationResponse } from '@/types/backendNotification'
import { NotificationType, type NotificationItem, type NotificationType as UiNotificationType } from '@/types/management'

const toNotificationType = (type: string): UiNotificationType => {
  if (type.startsWith('SCHEDULE')) return NotificationType.SCHEDULE
  if (type.startsWith('LEAVE')) return NotificationType.LEAVE
  if (type.startsWith('PREFERENCE')) return NotificationType.PREFERENCE
  if (type.startsWith('SWAP')) return NotificationType.SWAP
  if (type in NotificationType) return type as UiNotificationType
  return NotificationType.GENERAL
}

export const adaptBackendNotification = (response: BackendNotificationResponse): NotificationItem => ({
  id: String(response.id),
  title: response.title,
  content: response.content,
  type: toNotificationType(response.notificationType),
  priority: 'MEDIUM',
  recipient: 'Bạn',
  recipientType: 'SELECTED',
  createdAt: response.createdAt,
  isRead: response.read,
})

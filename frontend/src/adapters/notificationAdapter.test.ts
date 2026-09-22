import { describe, expect, test } from 'vitest'

import { adaptBackendNotification } from '@/adapters/notificationAdapter'
import type { BackendNotificationResponse } from '@/types/backendNotification'

const baseResponse: BackendNotificationResponse = {
  id: 10,
  title: 'Đơn nghỉ đã được duyệt',
  content: 'Đơn nghỉ của bạn đã được duyệt.',
  notificationType: 'LEAVE_APPROVED',
  read: false,
  createdAt: '2026-09-23T08:00:00',
}

describe('adaptBackendNotification', () => {
  test('maps backend notification fields to UI item', () => {
    expect(adaptBackendNotification(baseResponse)).toEqual({
      id: '10',
      title: 'Đơn nghỉ đã được duyệt',
      content: 'Đơn nghỉ của bạn đã được duyệt.',
      type: 'LEAVE',
      priority: 'MEDIUM',
      recipient: 'Bạn',
      recipientType: 'SELECTED',
      createdAt: '2026-09-23T08:00:00',
      isRead: false,
    })
  })

  test('falls back unknown notification types to GENERAL', () => {
    expect(adaptBackendNotification({ ...baseResponse, notificationType: 'CUSTOM_EVENT' }).type).toBe('GENERAL')
  })
})

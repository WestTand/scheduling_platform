import { describe, expect, test } from 'vitest'

import { adaptBackendActivityLog } from '@/adapters/activityLogAdapter'

describe('adaptBackendActivityLog', () => {
  test('maps known backend activity log values to friendly UI text', () => {
    expect(adaptBackendActivityLog({
      id: 5,
      accountId: 10,
      username: 'manager',
      action: 'APPROVE',
      entityType: 'leave_requests',
      entityId: 2,
      oldValueJson: null,
      newValueJson: '{"status":"APPROVED"}',
      createdAt: '2026-09-23T08:30:00',
      ipAddress: null,
    })).toEqual({
      id: '5',
      timestamp: '2026-09-23T08:30:00',
      actorName: 'manager',
      actorRole: 'MANAGER',
      action: 'Duyệt',
      target: 'Đơn nghỉ phép #2',
      description: 'Quản lý đã duyệt đơn nghỉ phép.',
      status: 'SUCCESS',
      ipAddress: '',
    })
  })

  test('falls back to readable text for unknown backend values', () => {
    expect(adaptBackendActivityLog({
      id: 6,
      accountId: 10,
      username: 'manager',
      action: 'ARCHIVE',
      entityType: 'custom_records',
      entityId: null,
      oldValueJson: '{"status":"OPEN"}',
      newValueJson: '{"status":"ARCHIVED"}',
      createdAt: '2026-09-23T09:00:00',
      ipAddress: '10.0.0.12',
    })).toEqual({
      id: '6',
      timestamp: '2026-09-23T09:00:00',
      actorName: 'manager',
      actorRole: 'MANAGER',
      action: 'ARCHIVE',
      target: 'custom_records',
      description: 'Đã thực hiện thao tác ARCHIVE trên custom_records.',
      status: 'SUCCESS',
      ipAddress: '10.0.0.12',
    })
  })
})

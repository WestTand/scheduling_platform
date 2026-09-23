import { describe, expect, test } from 'vitest'

import { adaptBackendActivityLog } from '@/adapters/activityLogAdapter'

describe('adaptBackendActivityLog', () => {
  test('maps backend activity log to UI log', () => {
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
      action: 'APPROVE',
      target: 'leave_requests #2',
      description: '{"status":"APPROVED"}',
      status: 'SUCCESS',
      ipAddress: '',
    })
  })
})

import { describe, expect, test } from 'vitest'

import { adaptBackendSwapRequest, adaptBackendScheduleAssignment } from '@/adapters/swapRequestAdapter'
import type { BackendSwapRequestResponse, BackendEmployeeScheduleItemResponse } from '@/types/backendSwapRequest'

describe('swapRequestAdapter', () => {
  test('preserves pending target status and employee ids', () => {
    const response: BackendSwapRequestResponse = {
      id: 15,
      requesterAssignmentId: 100,
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'Nguyen Van A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'Tran Van B' },
      reason: 'Can doi ca',
      status: 'PENDING_TARGET',
      reviewedByAccountId: null,
      reviewedByUsername: null,
      reviewedAt: null,
    }

    expect(adaptBackendSwapRequest(response)).toMatchObject({
      id: '15',
      requesterEmployeeId: '7',
      targetEmployeeId: '9',
      requesterAssignmentId: '100',
      targetAssignmentId: undefined,
      employeeName: 'Nguyen Van A',
      partnerName: 'Tran Van B',
      status: 'PENDING_TARGET',
      reason: 'Can doi ca',
    })
  })

  test('maps employee schedule item into assignment option', () => {
    const item: BackendEmployeeScheduleItemResponse = {
      assignmentId: 200,
      assignmentStatus: 'ASSIGNED',
      assignedSource: 'AUTO',
      locked: false,
      workDate: '2026-09-22',
      shiftTypeId: 3,
      shiftTypeName: 'Truc sang',
      shiftStartTime: '08:00:00',
      shiftEndTime: '16:00:00',
      startsAt: '2026-09-22T01:00:00Z',
      endsAt: '2026-09-22T09:00:00Z',
      workMinutes: 480,
      isTransitionShift: false,
      periodId: 4,
      periodName: 'Tuan 39',
      periodStatus: 'PUBLISHED',
    }

    expect(adaptBackendScheduleAssignment(item)).toEqual({
      id: '200',
      workDate: '2026-09-22',
      shiftName: 'Truc sang',
      shiftStartTime: '08:00:00',
      shiftEndTime: '16:00:00',
      periodName: 'Tuan 39',
      periodStatus: 'PUBLISHED',
    })
  })
})

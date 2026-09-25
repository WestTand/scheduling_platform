import { describe, expect, test } from 'vitest'
import { adaptBackendEmployeeSchedule, getEmployeeWeekWindow } from './EmployeeSchedulePage.helpers'

describe('EmployeeSchedulePage helpers', () => {
  test('resolves current week window for backend query', () => {
    expect(getEmployeeWeekWindow(0, new Date(2026, 8, 25))).toEqual({ from: '2026-09-21', to: '2026-09-27' })
  })

  test('adapts backend schedule items to view items', () => {
    const result = adaptBackendEmployeeSchedule([
      { assignmentId: 1, assignmentStatus: 'ASSIGNED', assignedSource: 'AUTO', locked: false, workDate: '2026-09-25', shiftTypeId: 1, shiftTypeName: 'Thong tam', shiftStartTime: '08:00:00', shiftEndTime: '17:00:00', startsAt: null, endsAt: null, workMinutes: 480, isTransitionShift: false, periodId: 7, periodName: 'Tuan 1', periodStatus: 'PUBLISHED' },
    ])
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({ id: '1', date: '2026-09-25', shiftCode: 'THONG_TAM', shiftName: 'Thong tam' })
  })

  test('returns empty list for empty backend schedule', () => {
    expect(adaptBackendEmployeeSchedule([])).toEqual([])
  })
})

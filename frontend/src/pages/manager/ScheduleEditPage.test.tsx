import { describe, expect, it, vi } from 'vitest'

import {
  canRunScheduleEditOptimize,
  filterScheduleEditAssignments,
  getScheduleEditNoticeClass,
  getScheduleEditLockedMessage,
  getScheduleEditModeLabel,
  reloadScheduleEditDetail,
  shouldShowMockStatusActions,
} from './ScheduleEditPage.helpers'

import type { ScheduleAssignment, ScheduleDetail } from '@/types/schedule'

describe('ScheduleEditPage helpers', () => {
  it('uses backend-mode copy and hides mock status actions for backend mode', () => {
    expect(getScheduleEditModeLabel(false)).toBe('Backend mode: chỉnh sửa phân công trực tiếp')
    expect(shouldShowMockStatusActions(false)).toBe(false)
  })

  it('keeps mock-mode copy and status actions for mock mode', () => {
    expect(getScheduleEditModeLabel(true)).toBe('Chế độ chỉnh sửa mock')
    expect(shouldShowMockStatusActions(true)).toBe(true)
  })

  it('enables re-optimize only for editable backend schedules', () => {
    expect(canRunScheduleEditOptimize({ useMockApi: false, scheduleId: '7', isEditingLocked: false, isOptimizing: false })).toBe(true)
    expect(canRunScheduleEditOptimize({ useMockApi: true, scheduleId: '7', isEditingLocked: false, isOptimizing: false })).toBe(false)
    expect(canRunScheduleEditOptimize({ useMockApi: false, scheduleId: '', isEditingLocked: false, isOptimizing: false })).toBe(false)
    expect(canRunScheduleEditOptimize({ useMockApi: false, scheduleId: '7', isEditingLocked: true, isOptimizing: false })).toBe(false)
    expect(canRunScheduleEditOptimize({ useMockApi: false, scheduleId: '7', isEditingLocked: false, isOptimizing: true })).toBe(false)
  })

  it('locks published and cancelled backend schedules only', () => {
    expect(getScheduleEditLockedMessage(false, 'PUBLISHED')).toBe('Lịch đã công bố nên không thể chỉnh sửa phân công.')
    expect(getScheduleEditLockedMessage(false, 'CANCELLED')).toBe('Lịch đã hủy nên không thể chỉnh sửa phân công.')
    expect(getScheduleEditLockedMessage(false, 'DRAFT')).toBe('')
    expect(getScheduleEditLockedMessage(true, 'PUBLISHED')).toBe('')
  })

  it('styles error notices in red and success notices in blue', () => {
    expect(getScheduleEditNoticeClass('error')).toContain('border-red-200')
    expect(getScheduleEditNoticeClass('success')).toContain('border-blue-200')
  })

  it('filters assignments by unassigned and warning status', () => {
    const assignments: ScheduleAssignment[] = [
      { id: 'assigned-ok', scheduleId: '1', date: '2026-09-21', shiftCode: 'THONG_TAM', employeeNames: ['Nguyễn Văn An'], warningCount: 0 },
      { id: 'unassigned', scheduleId: '1', date: '2026-09-21', shiftCode: 'TRUC_SANG', employeeNames: [], warningCount: 1 },
      { id: 'assigned-warning', scheduleId: '1', date: '2026-09-21', shiftCode: 'TRUC_CHIEU', employeeNames: ['Trần Thị Bình'], warningCount: 2 },
    ]

    expect(filterScheduleEditAssignments(assignments, 'ALL').map((assignment) => assignment.id)).toEqual(['assigned-ok', 'unassigned', 'assigned-warning'])
    expect(filterScheduleEditAssignments(assignments, 'UNASSIGNED').map((assignment) => assignment.id)).toEqual(['unassigned'])
    expect(filterScheduleEditAssignments(assignments, 'WARNING').map((assignment) => assignment.id)).toEqual(['unassigned', 'assigned-warning'])
  })

  it('reloads detail and assignments together after backend mutations', async () => {
    const refreshedAssignments: ScheduleAssignment[] = [{
      id: 'assignment-1',
      scheduleId: '1',
      date: '2026-09-21',
      shiftCode: 'THONG_TAM',
      employeeNames: ['Nguyễn Văn An'],
      warningCount: 0,
    }]
    const refreshedDetail = {
      id: '1',
      name: 'Tuần mẫu tháng 09/2026',
      startDate: '2026-09-21',
      endDate: '2026-09-27',
      createdBy: 'Backend',
      createdAt: '2026-09-21T00:00:00',
      employeeCount: 1,
      assignmentCount: 1,
      shiftCount: 1,
      status: 'DRAFT',
      isMock: false,
      employeePool: ['Nguyễn Văn An'],
      assignments: refreshedAssignments,
      warnings: [],
    } satisfies ScheduleDetail
    const setBackendResult = vi.fn()
    const setAssignments = vi.fn()

    const result = await reloadScheduleEditDetail({
      scheduleId: '1',
      loadDetail: vi.fn().mockResolvedValue(refreshedDetail),
      setBackendResult,
      setAssignments,
    })

    expect(result).toBe(refreshedDetail)
    expect(setBackendResult).toHaveBeenCalledWith({ scheduleId: '1', detail: refreshedDetail, error: '' })
    expect(setAssignments).toHaveBeenCalledWith(refreshedAssignments)
  })
})

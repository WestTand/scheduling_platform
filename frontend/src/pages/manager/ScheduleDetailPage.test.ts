import { describe, expect, it } from 'vitest'

import { canApproveSchedule, getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage } from './ScheduleDetailPage.helpers'

describe('ScheduleDetailPage helpers', () => {
  it('shows backend status actions by schedule status', () => {
    expect(getScheduleDetailActions('DRAFT')).toEqual(['approve', 'cancel'])
    expect(getScheduleDetailActions('APPROVED')).toEqual(['publish', 'cancel'])
    expect(getScheduleDetailActions('PUBLISHED')).toEqual([])
    expect(getScheduleDetailActions('CANCELLED')).toEqual([])
  })

  it('blocks approval while the latest optimization run is infeasible', () => {
    expect(canApproveSchedule('DRAFT', 'INFEASIBLE')).toBe(false)
    expect(canApproveSchedule('DRAFT', 'SUCCESS')).toBe(true)
    expect(canApproveSchedule('DRAFT', 'FAILED')).toBe(true)
    expect(canApproveSchedule('DRAFT', null)).toBe(true)
    expect(canApproveSchedule('PUBLISHED', 'INFEASIBLE')).toBe(false)
  })

  it('explains why an infeasible schedule cannot be approved', () => {
    expect(getScheduleDetailReadOnlyMessage('DRAFT', 'INFEASIBLE'))
      .toBe('Lịch đang bị cảnh báo (INFE) nên không thể duyệt. Hãy chạy lại AI Optimize hoặc chỉnh lịch trước khi duyệt.')
    expect(getScheduleDetailReadOnlyMessage('DRAFT', 'SUCCESS')).toBe('')
  })

  it('shows read-only messages for immutable statuses', () => {
    expect(getScheduleDetailReadOnlyMessage('PUBLISHED')).toBe('Lịch đã công bố nên trang chi tiết chỉ cho phép xem.')
    expect(getScheduleDetailReadOnlyMessage('CANCELLED')).toBe('Lịch đã hủy nên trang chi tiết chỉ cho phép xem.')
    expect(getScheduleDetailReadOnlyMessage('DRAFT')).toBe('')
  })

  it('styles success and error notices distinctly', () => {
    expect(getScheduleDetailNoticeClass('success')).toContain('border-blue-200')
    expect(getScheduleDetailNoticeClass('error')).toContain('border-red-200')
  })
})

import { describe, expect, it } from 'vitest'

import { getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage } from './ScheduleDetailPage.helpers'

describe('ScheduleDetailPage helpers', () => {
  it('shows backend status actions by schedule status', () => {
    expect(getScheduleDetailActions('DRAFT')).toEqual(['approve', 'cancel'])
    expect(getScheduleDetailActions('APPROVED')).toEqual(['publish', 'cancel'])
    expect(getScheduleDetailActions('PUBLISHED')).toEqual([])
    expect(getScheduleDetailActions('CANCELLED')).toEqual([])
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

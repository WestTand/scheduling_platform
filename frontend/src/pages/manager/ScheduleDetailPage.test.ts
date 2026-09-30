import { describe, expect, it } from 'vitest'

import { canApproveSchedule, getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage, getScheduleDetailStaffingMessage } from './ScheduleDetailPage.helpers'
import type { ScheduleStaffingAdvice } from '@/types/schedule'

const advice = (overrides: Partial<ScheduleStaffingAdvice> = {}): ScheduleStaffingAdvice => ({
  requiredEmployees: 10,
  availableEmployees: 1,
  totalShifts: 21,
  exact: true,
  reasons: ['QUOTA_OVERTIME'],
  ...overrides,
})

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

describe('getScheduleDetailStaffingMessage', () => {
  it('explains the staffing shortfall for an infeasible run with advice', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice()))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1. Lý do chính: hạn mức ca ngoài giờ mỗi tuần.')
  })

  it('stays silent when an infeasible run carries no staffing advice', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', null)).toBe('')
    expect(getScheduleDetailStaffingMessage('INFEASIBLE')).toBe('')
  })

  it('stays silent for runs that are not infeasible', () => {
    expect(getScheduleDetailStaffingMessage('SUCCESS', advice())).toBe('')
    expect(getScheduleDetailStaffingMessage('FAILED', advice())).toBe('')
    expect(getScheduleDetailStaffingMessage(null, advice())).toBe('')
  })

  it('omits the main-reason clause for reason codes without a known label', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ reasons: ['SOMETHING_NEW'] })))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1.')
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ reasons: [] })))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1.')
  })

  it('flags a non-exact requirement as an estimate the AI could not prove', () => {
    const message = getScheduleDetailStaffingMessage('INFEASIBLE', advice({ exact: false }))
    expect(message).toContain('ước tính')
    expect(message).toContain('10')
    expect(message).toContain('1')
    expect(message).not.toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1. Lý do chính: hạn mức ca ngoài giờ mỗi tuần.')
  })

  it('uses the first recognised reason code when several are reported', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ reasons: ['SOMETHING_NEW', 'WEEKLY_CAPACITY', 'SAME_DAY_REST'] })))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1. Lý do chính: giới hạn số ca mỗi tuần.')
  })

  it('renders zero counts without crashing', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ requiredEmployees: 0, availableEmployees: 0, reasons: [] })))
      .toBe('Lịch này cần tối thiểu 0 nhân viên nhưng pool hiện chỉ có 0.')
  })
})

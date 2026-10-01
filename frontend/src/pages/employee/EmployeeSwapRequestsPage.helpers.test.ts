import { describe, expect, it } from 'vitest'

import { formatSwapAssignmentOptionLabel, getRespondBlockedMessage, resolvePublishedAssignmentId } from './EmployeeSwapRequestsPage.helpers'
import type { SwapAssignmentOption } from '@/types/management'

const publishedAssignment: SwapAssignmentOption = {
  id: '42',
  workDate: '2026-05-04',
  shiftName: 'Ca sáng',
  shiftStartTime: '07:00',
  shiftEndTime: '13:00',
  periodName: 'Kỳ tháng 5',
  periodStatus: 'PUBLISHED',
}

const draftAssignment: SwapAssignmentOption = {
  id: '43',
  workDate: '2026-05-05',
  shiftName: 'Ca tối',
  shiftStartTime: '14:00',
  shiftEndTime: '22:00',
  periodName: 'Kỳ tháng 6',
  periodStatus: 'DRAFT',
}

const options = [publishedAssignment, draftAssignment]

describe('formatSwapAssignmentOptionLabel', () => {
  it('renders date, shift name with times and period name', () => {
    expect(formatSwapAssignmentOptionLabel(publishedAssignment)).toBe('2026-05-04 • Ca sáng 07:00-13:00 • Kỳ tháng 5 (PUBLISHED)')
  })
})

describe('resolvePublishedAssignmentId', () => {
  it('returns the id of a shift in a published period', () => {
    expect(resolvePublishedAssignmentId(options, '42')).toBe('42')
  })

  it('returns null for a shift that is not in a published period', () => {
    expect(resolvePublishedAssignmentId(options, '43')).toBeNull()
  })

  it('returns null when no shift is selected', () => {
    expect(resolvePublishedAssignmentId(options, '')).toBeNull()
  })

  it('returns null when the selected id is missing from the options', () => {
    expect(resolvePublishedAssignmentId(options, '99')).toBeNull()
  })
})

describe('getRespondBlockedMessage', () => {
  it('warns when accepting without selecting a shift', () => {
    expect(getRespondBlockedMessage(options, '', true)).toBe('Vui lòng chọn ca của bạn để đổi trước khi xác nhận.')
  })

  it('does not block rejecting', () => {
    expect(getRespondBlockedMessage(options, '', false)).toBeNull()
  })

  it('does not block when a published shift is selected', () => {
    expect(getRespondBlockedMessage(options, '42', true)).toBeNull()
  })

  it('warns when the selected shift is not in a published period', () => {
    expect(getRespondBlockedMessage(options, '43', true)).toBe('Vui lòng chọn ca của bạn để đổi trước khi xác nhận.')
  })

  it('warns when the selected id is missing from the options', () => {
    expect(getRespondBlockedMessage(options, '99', true)).toBe('Vui lòng chọn ca của bạn để đổi trước khi xác nhận.')
  })

  it('warns while the shifts are still loading', () => {
    expect(getRespondBlockedMessage(options, '42', true, true)).toBe('Đang tải ca làm của bạn, vui lòng đợi.')
  })
})

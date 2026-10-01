import { describe, expect, it } from 'vitest'

import { formatSwapAssignmentOptionLabel, getPublishedSwapAssignmentOptions, getRespondConfirmation, resolveTargetAssignmentId } from './EmployeeSwapRequestsPage.helpers'
import type { RespondDialogState } from './EmployeeSwapRequestsPage.helpers'
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

const dialog = (overrides: Partial<RespondDialogState> = {}): RespondDialogState => ({
  options,
  selectedId: '',
  loading: false,
  error: '',
  ...overrides,
})

describe('formatSwapAssignmentOptionLabel', () => {
  it('renders date, shift name with times and period name', () => {
    expect(formatSwapAssignmentOptionLabel(publishedAssignment)).toBe('2026-05-04 • Ca sáng 07:00-13:00 • Kỳ tháng 5 (PUBLISHED)')
  })
})

describe('getPublishedSwapAssignmentOptions', () => {
  it('keeps only shifts in published periods', () => {
    expect(getPublishedSwapAssignmentOptions(options)).toEqual([publishedAssignment])
  })

  it('returns an empty list when every shift is unpublished', () => {
    expect(getPublishedSwapAssignmentOptions([draftAssignment])).toEqual([])
  })
})

describe('resolveTargetAssignmentId', () => {
  it('returns the numeric id of a shift in a published period', () => {
    expect(resolveTargetAssignmentId(options, '42')).toBe(42)
  })

  it('returns null for a shift that is not in a published period', () => {
    expect(resolveTargetAssignmentId(options, '43')).toBeNull()
  })

  it('returns null when no shift is selected', () => {
    expect(resolveTargetAssignmentId(options, '')).toBeNull()
  })

  it('returns null when the selection is whitespace only', () => {
    expect(resolveTargetAssignmentId(options, '   ')).toBeNull()
  })

  it('returns null when the selected id is missing from the options', () => {
    expect(resolveTargetAssignmentId(options, '99')).toBeNull()
  })

  it('returns null when the selected id is not numeric', () => {
    expect(resolveTargetAssignmentId([{ ...publishedAssignment, id: 'assignment-01' }], 'assignment-01')).toBeNull()
  })
})

describe('getRespondConfirmation', () => {
  it('allows accepting with a published shift selected', () => {
    expect(getRespondConfirmation(true, dialog({ selectedId: '42' }))).toEqual({ allowed: true, targetAssignmentId: 42 })
  })

  it('warns when accepting without selecting a shift', () => {
    expect(getRespondConfirmation(true, dialog())).toEqual({ allowed: false, variant: 'warning', message: 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' })
  })

  it('warns when the selection is whitespace only', () => {
    expect(getRespondConfirmation(true, dialog({ selectedId: '   ' }))).toEqual({ allowed: false, variant: 'warning', message: 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' })
  })

  it('warns when the selected shift is not in a published period', () => {
    expect(getRespondConfirmation(true, dialog({ selectedId: '43' }))).toEqual({ allowed: false, variant: 'warning', message: 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' })
  })

  it('warns when the selected id is missing from the options', () => {
    expect(getRespondConfirmation(true, dialog({ selectedId: '99' }))).toEqual({ allowed: false, variant: 'warning', message: 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' })
  })

  it('explains that there is nothing to swap when no shift is published', () => {
    expect(getRespondConfirmation(true, dialog({ options: [draftAssignment] }))).toEqual({ allowed: false, variant: 'warning', message: 'Bạn không có ca nào đã xuất bản để đổi.' })
  })

  it('explains that there is nothing to swap when the list came back empty', () => {
    expect(getRespondConfirmation(true, dialog({ options: [] }))).toEqual({ allowed: false, variant: 'warning', message: 'Bạn không có ca nào đã xuất bản để đổi.' })
  })

  it('warns while the shifts are still loading', () => {
    expect(getRespondConfirmation(true, dialog({ loading: true, options: [] }))).toEqual({ allowed: false, variant: 'warning', message: 'Đang tải ca làm của bạn, vui lòng đợi.' })
  })

  it('reports the real failure message instead of asking for a shift when loading failed', () => {
    expect(getRespondConfirmation(true, dialog({ error: 'Không thể tải ca làm của bạn.', options: [] }))).toEqual({ allowed: false, variant: 'error', message: 'Không thể tải ca làm của bạn.' })
  })

  it('reports the load failure even when a stale shift is still selected', () => {
    expect(getRespondConfirmation(true, dialog({ error: 'Máy chủ không phản hồi.', selectedId: '42' }))).toEqual({ allowed: false, variant: 'error', message: 'Máy chủ không phản hồi.' })
  })

  it('never blocks rejecting', () => {
    expect(getRespondConfirmation(false, null)).toEqual({ allowed: true, targetAssignmentId: undefined })
  })

  it('never blocks rejecting even when the dialog failed to load', () => {
    expect(getRespondConfirmation(false, dialog({ error: 'Không thể tải ca làm của bạn.', options: [] }))).toEqual({ allowed: true, targetAssignmentId: undefined })
  })

  it('warns when accepting while the dialog state is gone', () => {
    expect(getRespondConfirmation(true, null)).toEqual({ allowed: false, variant: 'warning', message: 'Vui lòng chọn ca của bạn để đổi trước khi xác nhận.' })
  })
})
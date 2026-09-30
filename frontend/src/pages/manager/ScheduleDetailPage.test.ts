import { describe, expect, it } from 'vitest'

import { canApproveSchedule, getPoolEditorCandidates, getScheduleDetailActions, getScheduleDetailNoticeClass, getScheduleDetailReadOnlyMessage, getScheduleDetailStaffingMessage } from './ScheduleDetailPage.helpers'
import type { ScheduleStaffingAdvice } from '@/types/schedule'

const UNREACHABLE_STAFFING_MESSAGE = 'Không có số lượng nhân viên nào xếp được lịch này: có quy tắc khiến lịch không thể thực hiện, nên tăng pool lên bao nhiêu người cũng không giải quyết được.'

const advice = (overrides: Partial<ScheduleStaffingAdvice> = {}): ScheduleStaffingAdvice => ({
  requiredEmployees: 10,
  availableEmployees: 1,
  totalShifts: 21,
  exact: true,
  unreachable: false,
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

  it('offers only AI-schedulable employees as pool editor candidates', () => {
    expect(getPoolEditorCandidates([
      { id: '31', fullName: 'AI Ready', employeeCode: 'EMP031', email: '', phone: '', role: 'EMPLOYEE', status: 'ACTIVE', joinDate: '2026-09-01', aiSchedulable: true },
      { id: '32', fullName: 'Missing Account', employeeCode: 'EMP032', email: '', phone: '', role: 'EMPLOYEE', status: 'ACTIVE', joinDate: '2026-09-01', aiSchedulable: false },
      { id: '33', fullName: 'Unknown Flag', employeeCode: 'EMP033', email: '', phone: '', role: 'EMPLOYEE', status: 'ACTIVE', joinDate: '2026-09-01' },
    ])).toEqual([{ id: '31', fullName: 'AI Ready', employeeCode: 'EMP031' }])
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

  it('labels the unproven requirement as a lower-bound estimate instead of a target', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ exact: false, reasons: [] })))
      .toBe('Lịch này ước tính cần ít nhất 10 nhân viên (AI chưa chứng minh được con số chính xác) nhưng pool hiện chỉ có 1.')
  })

  it('does not describe staffing advice as a pool shortfall when the pool already meets the structural minimum', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ availableEmployees: 12, reasons: [] })))
      .toBe('Pool hiện có 12 nhân viên, không thấp hơn mức tối thiểu 10 mà AI tính được. Lịch vẫn không xếp được, có thể do nghỉ phép, phân công khóa hoặc ràng buộc theo từng ca; hãy kiểm tra cảnh báo chi tiết.')
  })

  it('labels a non-exact structural count as an estimate when the pool already meets it', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ availableEmployees: 12, exact: false, reasons: [] })))
      .toBe('Pool hiện có 12 nhân viên, không thấp hơn mức ước tính 10 mà AI tính được. Lịch vẫn không xếp được, có thể do nghỉ phép, phân công khóa hoặc ràng buộc theo từng ca; hãy kiểm tra cảnh báo chi tiết.')
  })

  it('warns that no headcount can staff the schedule when the run is unreachable', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, reasons: [] })))
      .toBe(UNREACHABLE_STAFFING_MESSAGE)
  })

  it('does not quote a headcount target or tell the manager to add people when the run is unreachable', () => {
    const message = getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, requiredEmployees: 99, availableEmployees: 1, reasons: [] }))

    expect(message).not.toContain('99')
    expect(message).not.toMatch(/cần (tối thiểu|ít nhất)/)
    expect(message).not.toMatch(/(hãy|nên) thêm (người|nhân viên) vào pool/i)
  })

  it('points at the reason codes that make the schedule unschedulable', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, reasons: ['SAME_DAY_REST'] })))
      .toBe(`${UNREACHABLE_STAFFING_MESSAGE} Lý do chính: thời gian nghỉ tối thiểu giữa các ca.`)
  })

  it('omits the main-reason clause for unreachable advice with unrecognised reason codes', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, reasons: ['SOMETHING_NEW'] })))
      .toBe(UNREACHABLE_STAFFING_MESSAGE)
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, reasons: [] })))
      .toBe(UNREACHABLE_STAFFING_MESSAGE)
  })

  it('prefers the unreachable wording over the exact wording', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, exact: true, reasons: [] })))
      .toBe(UNREACHABLE_STAFFING_MESSAGE)
  })

  it('prefers the unreachable wording over the estimate wording', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ unreachable: true, exact: false, reasons: [] })))
      .toBe(UNREACHABLE_STAFFING_MESSAGE)
  })

  it('uses the first recognised reason code when several are reported', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ reasons: ['SOMETHING_NEW', 'WEEKLY_CAPACITY', 'SAME_DAY_REST'] })))
      .toBe('Lịch này cần tối thiểu 10 nhân viên nhưng pool hiện chỉ có 1. Lý do chính: giới hạn số ca mỗi tuần.')
  })

  it('renders zero counts without crashing', () => {
    expect(getScheduleDetailStaffingMessage('INFEASIBLE', advice({ requiredEmployees: 0, availableEmployees: 0, reasons: [] })))
      .toBe('Pool hiện có 0 nhân viên, không thấp hơn mức tối thiểu 0 mà AI tính được. Lịch vẫn không xếp được, có thể do nghỉ phép, phân công khóa hoặc ràng buộc theo từng ca; hãy kiểm tra cảnh báo chi tiết.')
  })
})

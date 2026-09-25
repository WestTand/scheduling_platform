import { describe, expect, test } from 'vitest'

import { adaptBackendPeriodSchedule, adaptBackendSchedulePeriod } from '@/adapters/scheduleAdapter'
import { ScheduleStatus } from '@/types/schedule'

describe('scheduleAdapter', () => {
  test('maps backend schedule period to UI schedule', () => {
    expect(adaptBackendSchedulePeriod({
      id: 7,
      periodName: 'Tuần mẫu tháng 09/2026',
      startDate: '2026-09-21',
      endDate: '2026-09-27',
      versionNo: 1,
      status: 'DRAFT',
      createdByAccountId: 1,
      createdByUsername: 'manager@example.com',
      approvedByAccountId: null,
      approvedByUsername: null,
      publishedAt: null,
      totalPenalty: null,
    })).toEqual({
      id: '7',
      name: 'Tuần mẫu tháng 09/2026',
      startDate: '2026-09-21',
      endDate: '2026-09-27',
      createdBy: 'manager@example.com',
      createdAt: '2026-09-21T00:00:00',
      employeeCount: 0,
      assignmentCount: 0,
      shiftCount: 0,
      status: ScheduleStatus.DRAFT,
      isMock: false,
    })
  })

  test('preserves cancelled backend schedule status', () => {
    expect(adaptBackendSchedulePeriod({
      id: 8,
      periodName: 'Kỳ đã hủy',
      startDate: '2026-09-28',
      endDate: '2026-10-04',
      versionNo: 1,
      status: 'CANCELLED',
      createdByAccountId: 1,
      createdByUsername: 'manager@example.com',
      approvedByAccountId: null,
      approvedByUsername: null,
      publishedAt: null,
      totalPenalty: null,
    }).status).toBe('CANCELLED')
  })

  test('maps backend period schedule to UI detail assignments', () => {
    const detail = adaptBackendPeriodSchedule({
      periodId: 7,
      periodName: 'Tuần mẫu tháng 09/2026',
      status: 'DRAFT',
      startDate: '2026-09-21',
      endDate: '2026-09-27',
      employeePool: [],
      workShifts: [
        {
          id: 11,
          workDate: '2026-09-21',
          shiftTypeId: 100,
          shiftName: 'Thong tam',
          shiftStartTime: '08:00:00',
          shiftEndTime: '17:00:00',
          startsAt: '2026-09-21T08:00:00+07:00',
          endsAt: '2026-09-21T17:00:00+07:00',
          workMinutes: 480,
          isTransitionShift: false,
          overnight: false,
          requiredStaff: 1,
          maxStaff: 20,
          assignments: [
            { id: 21, workShiftId: 11, periodId: 7, employeeId: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: false },
          ],
        },
      ],
    })

    expect(detail.assignmentCount).toBe(1)
    expect(detail.shiftCount).toBe(1)
    expect(detail.employeeCount).toBe(1)
    expect(detail.assignments).toEqual([{ id: '21', scheduleId: '7', workShiftId: '11', employeeId: '31', date: '2026-09-21', shiftCode: 'THONG_TAM', employeeNames: ['Nguyễn Văn An'], warningCount: 0, locked: false }])
  })

  test('keeps unassigned backend work shifts visible in UI detail', () => {
    const detail = adaptBackendPeriodSchedule({
      periodId: 8,
      periodName: 'Lịch chưa phân công',
      status: 'DRAFT',
      startDate: '2026-09-21',
      endDate: '2026-09-21',
      employeePool: [{ id: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An' }],
      workShifts: [
        {
          id: 12,
          workDate: '2026-09-21',
          shiftTypeId: 100,
          shiftName: 'Thong tam',
          shiftStartTime: '08:00:00',
          shiftEndTime: '17:00:00',
          startsAt: '2026-09-21T08:00:00+07:00',
          endsAt: '2026-09-21T17:00:00+07:00',
          workMinutes: 480,
          isTransitionShift: false,
          overnight: false,
          requiredStaff: 2,
          maxStaff: 20,
          assignments: [],
        },
      ],
    })

    expect(detail.shiftCount).toBe(1)
    expect(detail.assignmentCount).toBe(0)
    expect(detail.employeeCount).toBe(1)
    expect(detail.employeePool).toEqual(['Nguyễn Văn An'])
    expect(detail.assignments).toEqual([{ id: 'work-shift-12', scheduleId: '8', workShiftId: '12', date: '2026-09-21', shiftCode: 'THONG_TAM', employeeNames: [], warningCount: 1, locked: false }])
    expect(detail.warnings).toEqual([
      {
        id: 'understaffed-shift-12',
        type: 'HARD_CONSTRAINT',
        severity: 'HIGH',
        employeeName: 'Pool: Nguyễn Văn An',
        date: '2026-09-21',
        shiftCode: 'THONG_TAM',
        content: 'Ca Thong tam ngày 21/09/2026 thiếu 2 nhân viên (cần 2, hiện có 0).',
        suggestion: 'Chọn thêm nhân viên phù hợp trong pool, giảm định mức nhân sự của ca, bỏ bớt loại ca/ngày, hoặc vào trang Chỉnh sửa để phân công thủ công.',
      },
    ])
  })

  test('marks partially staffed shifts with detailed shortage warning', () => {
    const detail = adaptBackendPeriodSchedule({
      periodId: 9,
      periodName: 'Lịch thiếu người',
      status: 'DRAFT',
      startDate: '2026-09-22',
      endDate: '2026-09-22',
      employeePool: [
        { id: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An' },
        { id: 32, employeeCode: 'EMP002', fullName: 'Trần Thị Bình' },
      ],
      workShifts: [
        {
          id: 13,
          workDate: '2026-09-22',
          shiftTypeId: 100,
          shiftName: 'Ngoai gio 1',
          shiftStartTime: '17:00:00',
          shiftEndTime: '00:30:00',
          startsAt: '2026-09-22T17:00:00+07:00',
          endsAt: '2026-09-23T00:30:00+07:00',
          workMinutes: 450,
          isTransitionShift: true,
          overnight: true,
          requiredStaff: 2,
          maxStaff: 20,
          assignments: [
            { id: 23, workShiftId: 13, periodId: 9, employeeId: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', assignmentStatus: 'ASSIGNED', assignedSource: 'AUTO', locked: false },
          ],
        },
      ],
    })

    expect(detail.assignments[0]?.warningCount).toBe(1)
    expect(detail.warnings[0]?.content).toBe('Ca Ngoai gio 1 ngày 22/09/2026 thiếu 1 nhân viên (cần 2, hiện có 1).')
  })
})

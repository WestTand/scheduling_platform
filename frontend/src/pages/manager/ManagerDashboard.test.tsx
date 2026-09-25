import { describe, expect, test } from 'vitest'

import { getManagerActionItems, getManagerSummaryCards } from './ManagerDashboard.helpers'

describe('ManagerDashboard helpers', () => {
  test('builds summary cards from real counts', () => {
    expect(getManagerSummaryCards({
      employeeTotal: 8,
      weekShiftCount: 4,
      pendingLeave: 1,
      pendingSwap: 2,
      pendingPreference: 0,
      warningCount: 3,
    })).toEqual([
      { label: 'Tổng nhân viên', value: '8', context: 'đang hoạt động', path: '/manager/employees' },
      { label: 'Ca trực tuần này', value: '4', context: 'tuần hiện tại', path: '/manager/schedules' },
      { label: 'Yêu cầu chờ duyệt', value: '3', context: 'nghỉ phép, đổi ca, nguyện vọng', path: '/manager/dashboard#action-center' },
      { label: 'Cảnh báo xung đột', value: '3', context: 'kỳ nháp tuần này', path: '/manager/schedules' },
    ])
  })

  test('builds action items with empty fallbacks', () => {
    const items = getManagerActionItems({
      draftPeriod: null,
      leave: null,
      swap: null,
      preference: null,
      criticalWarning: null,
    })
    expect(items).toHaveLength(5)
    expect(items[0]).toMatchObject({ title: 'Lịch nháp chờ xử lý', related: 'Không có lịch nháp', path: '/manager/schedules?status=DRAFT' })
    expect(items[4]).toMatchObject({ title: 'Cảnh báo lịch nghiêm trọng', related: 'Không có cảnh báo' })
  })

  test('maps real queue heads into action items', () => {
    const items = getManagerActionItems({
      draftPeriod: { id: '48', name: 'Test002' },
      leave: { employeeName: 'Nguyễn Văn An', startDate: '2026-09-24', endDate: '2026-09-25' },
      swap: { employeeName: 'Trần Thị Bình', reason: 'Cover me' },
      preference: { employeeName: 'Lê Minh Châu', note: 'Muốn nghỉ thứ 7' },
      criticalWarning: { periodId: '48', employeeName: 'Nguyễn Văn An', content: 'Ca Truc sang thiếu người' },
    })
    expect(items[1]).toMatchObject({ related: 'Nguyễn Văn An', path: '/manager/leave-requests?status=PENDING' })
    expect(items[2]).toMatchObject({ related: 'Trần Thị Bình', path: '/manager/swap-requests?status=PENDING_APPROVAL' })
    expect(items[3]).toMatchObject({ related: 'Lê Minh Châu', path: '/manager/preferences?status=PENDING' })
    expect(items[4]).toMatchObject({ path: '/manager/schedules/48?tab=warnings' })
  })
})

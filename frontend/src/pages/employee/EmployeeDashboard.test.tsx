import { describe, expect, test } from 'vitest'

import { getEmployeeSummaryCards, pickNextShiftDays } from './EmployeeDashboard.helpers'

describe('EmployeeDashboard helpers', () => {
  test('builds summary cards from real schedule and pending counts', () => {
    const cards = getEmployeeSummaryCards({
      todayShiftName: 'Ngoai gio 2',
      nextShiftName: 'Truc sang',
      totalMinutes: 2520,
      pendingLeave: 1,
      pendingPreference: 0,
      pendingSwap: 1,
    })
    expect(cards).toEqual([
      { label: 'Ca làm hôm nay', value: 'Ngoai gio 2', detail: 'Hôm nay' },
      { label: 'Ca làm tiếp theo', value: 'Truc sang', detail: 'Sắp tới' },
      { label: 'Tổng giờ làm', value: '42h', detail: 'Tuần này' },
      { label: 'Yêu cầu chờ xử lý', value: '2', detail: '1 đổi ca, 1 nghỉ phép' },
    ])
  })

  test('falls back when no shifts exist', () => {
    const cards = getEmployeeSummaryCards({ todayShiftName: null, nextShiftName: null, totalMinutes: 0, pendingLeave: 0, pendingPreference: 0, pendingSwap: 0 })
    expect(cards[0]).toMatchObject({ value: '—' })
    expect(cards[2]).toMatchObject({ value: '0h' })
  })

  test('picks next shift days with real assignment ids', () => {
    const days = pickNextShiftDays([
      { id: '7', date: '2026-09-25', day: 'Thứ Sáu', shiftCode: 'THONG_TAM', shiftName: 'Thong tam', timeRange: '08:00:00 - 17:00:00', status: 'ASSIGNED', warning: '' },
      { id: '9', date: '2026-09-26', day: 'Thứ Bảy', shiftCode: 'TRUC_SANG', shiftName: 'Truc sang', timeRange: '08:00:00 - 16:00:00', status: 'ASSIGNED', warning: '' },
    ], 3)
    expect(days.map((day) => day.id)).toEqual(['7', '9'])
    expect(days[0]).toMatchObject({ date: '2026-09-25', shiftName: 'Thong tam' })
  })
})

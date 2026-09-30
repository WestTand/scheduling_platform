import { describe, expect, it } from 'vitest'

import { getRuleRows } from './SchedulingRulesPage.helpers'
import type { SchedulingRules } from '@/types/schedulingRule'

const rules: SchedulingRules = {
  hardRules: {
    minRestHoursBetweenShifts: 12,
    maxWorkHoursPerDay: 12,
    maxShiftsPerWeek: 6,
  },
  quotas: {
    overtimeShiftsPerWeek: 1,
    bathingShiftsPerWeek: 2,
    onCallEveryNDays: 6,
  },
  softWeights: {
    fairness: 10,
    preference: 5,
    weekend: 5,
    heavyShift: 8,
    workHourBalance: 10,
  },
}

describe('getRuleRows', () => {
  it('describes hard rules with Vietnamese units', () => {
    expect(getRuleRows('hardRules', rules)).toEqual([
      { label: 'Giờ nghỉ tối thiểu giữa ca', value: '12 giờ', hint: '' },
      { label: 'Giờ làm tối đa mỗi ngày', value: '12 giờ', hint: '' },
      { label: 'Số ca tối đa mỗi tuần', value: '6 ca', hint: '' },
    ])
  })

  it('describes quotas with the on-call gap hint', () => {
    expect(getRuleRows('quotas', rules)).toEqual([
      { label: 'Số ngày ngoài giờ tối đa / tuần', value: '1 ngày', hint: '' },
      { label: 'Số ngày thông tầm tối đa / tuần', value: '2 ngày', hint: '' },
      { label: 'Khoảng cách tối thiểu giữa ngày trực', value: '6 ngày', hint: 'Số ngày giãn cách giữa hai ngày trực.' },
    ])
  })

  it('formats soft weights with two decimals', () => {
    const rows = getRuleRows('softWeights', rules)

    expect(rows).toHaveLength(5)
    expect(rows[0]).toEqual({ label: 'Công bằng', value: '10.00', hint: 'Ưu tiên phân bổ ca đồng đều.' })
    expect(rows[1]).toEqual({ label: 'Nguyện vọng', value: '5.00', hint: 'Ưu tiên đáp ứng nguyện vọng.' })
    expect(rows[2]).toEqual({ label: 'Cuối tuần', value: '5.00', hint: 'Ưu tiên cân bằng ca cuối tuần.' })
    expect(rows[3]).toEqual({ label: 'Ca nặng', value: '8.00', hint: 'Giảm tải ca nặng liên tục.' })
    expect(rows[4]).toEqual({ label: 'Cân bằng giờ làm', value: '10.00', hint: 'Ưu tiên tổng giờ làm đồng đều.' })
  })
})

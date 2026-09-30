import type { SchedulingRules } from '@/types/schedulingRule'

export interface RuleRow {
  label: string
  value: string
  hint: string
}

export type RuleSectionKey = 'hardRules' | 'quotas' | 'softWeights'

const twoDecimals = (value: number) => value.toFixed(2)

export function getRuleRows(section: RuleSectionKey, rules: SchedulingRules): RuleRow[] {
  if (section === 'hardRules') {
    return [
      { label: 'Giờ nghỉ tối thiểu giữa ca', value: `${rules.hardRules.minRestHoursBetweenShifts} giờ`, hint: '' },
      { label: 'Giờ làm tối đa mỗi ngày', value: `${rules.hardRules.maxWorkHoursPerDay} giờ`, hint: '' },
      { label: 'Số ca tối đa mỗi tuần', value: `${rules.hardRules.maxShiftsPerWeek} ca`, hint: '' },
    ]
  }

  if (section === 'quotas') {
    return [
      { label: 'Số ngày ngoài giờ tối đa / tuần', value: `${rules.quotas.overtimeShiftsPerWeek} ngày`, hint: '' },
      { label: 'Số ngày thông tầm tối đa / tuần', value: `${rules.quotas.bathingShiftsPerWeek} ngày`, hint: '' },
      {
        label: 'Khoảng cách tối thiểu giữa ngày trực',
        value: `${rules.quotas.onCallEveryNDays} ngày`,
        hint: 'Số ngày giãn cách giữa hai ngày trực.',
      },
    ]
  }

  return [
    { label: 'Công bằng', value: twoDecimals(rules.softWeights.fairness), hint: 'Ưu tiên phân bổ ca đồng đều.' },
    { label: 'Nguyện vọng', value: twoDecimals(rules.softWeights.preference), hint: 'Ưu tiên đáp ứng nguyện vọng.' },
    { label: 'Cuối tuần', value: twoDecimals(rules.softWeights.weekend), hint: 'Ưu tiên cân bằng ca cuối tuần.' },
    { label: 'Ca nặng', value: twoDecimals(rules.softWeights.heavyShift), hint: 'Giảm tải ca nặng liên tục.' },
    { label: 'Cân bằng giờ làm', value: twoDecimals(rules.softWeights.workHourBalance), hint: 'Ưu tiên tổng giờ làm đồng đều.' },
  ]
}

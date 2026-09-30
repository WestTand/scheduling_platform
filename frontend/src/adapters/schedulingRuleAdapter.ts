import type { BackendSchedulingRulesResponse } from '@/types/backendSchedulingRule'
import type { SchedulingRules } from '@/types/schedulingRule'

const decimal = (value: number | string) => Number(value)

export const adaptBackendSchedulingRules = (rules: BackendSchedulingRulesResponse): SchedulingRules => ({
  hardRules: {
    minRestHoursBetweenShifts: rules.hardRules.minRestHoursBetweenShifts,
    maxWorkHoursPerDay: rules.hardRules.maxWorkHoursPerDay,
    maxShiftsPerWeek: rules.hardRules.maxShiftsPerWeek,
  },
  quotas: {
    overtimeShiftsPerWeek: rules.quotas.overtimeShiftsPerWeek,
    bathingShiftsPerWeek: rules.quotas.bathingShiftsPerWeek,
    onCallEveryNDays: rules.quotas.onCallEveryNDays,
  },
  softWeights: {
    fairness: decimal(rules.softWeights.fairness),
    preference: decimal(rules.softWeights.preference),
    weekend: decimal(rules.softWeights.weekend),
    heavyShift: decimal(rules.softWeights.heavyShift),
    workHourBalance: decimal(rules.softWeights.workHourBalance),
  },
})

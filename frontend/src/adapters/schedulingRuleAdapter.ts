import type { BackendSchedulingRulesResponse, BackendUpdateSchedulingRulesRequest } from '@/types/backendSchedulingRule'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const decimal = (value: number | string) => Number(value)
const twoDecimals = (value: number) => Number(value.toFixed(2))

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

export const toBackendSchedulingRulesRequest = (rules: SchedulingRulesFormValues): BackendUpdateSchedulingRulesRequest => ({
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
    fairness: twoDecimals(rules.softWeights.fairness),
    preference: twoDecimals(rules.softWeights.preference),
    weekend: twoDecimals(rules.softWeights.weekend),
    heavyShift: twoDecimals(rules.softWeights.heavyShift),
    workHourBalance: twoDecimals(rules.softWeights.workHourBalance),
  },
})

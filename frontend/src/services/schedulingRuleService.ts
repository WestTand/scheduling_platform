import { apiConfig } from '@/config/api'
import { backendSchedulingRuleService } from '@/services/backendSchedulingRuleService'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const defaultRules: SchedulingRules = {
  hardRules: {
    minRestHoursBetweenShifts: 8,
    maxWorkHoursPerDay: 12,
    maxShiftsPerWeek: 6,
  },
  quotas: {
    overtimeShiftsPerWeek: 2,
    bathingShiftsPerWeek: 2,
    onCallEveryNDays: 3,
  },
  softWeights: {
    fairness: 1,
    preference: 1,
    weekend: 1,
    heavyShift: 1,
    workHourBalance: 1,
  },
}

let mockRules = clone(defaultRules)

export const schedulingRuleService = {
  async getRules(): Promise<SchedulingRules> {
    if (!apiConfig.useMockApi) {
      return backendSchedulingRuleService.getRules()
    }
    await delay(250)
    return clone(mockRules)
  },

  async updateRules(values: SchedulingRulesFormValues): Promise<SchedulingRules> {
    if (!apiConfig.useMockApi) {
      return backendSchedulingRuleService.updateRules(values)
    }
    await delay(300)
    mockRules = clone(values)
    return clone(mockRules)
  },

  reset() {
    mockRules = clone(defaultRules)
  },
}

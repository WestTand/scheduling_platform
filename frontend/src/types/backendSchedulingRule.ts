export interface BackendSchedulingHardRules {
  minRestHoursBetweenShifts: number
  maxWorkHoursPerDay: number
  maxShiftsPerWeek: number
}

export interface BackendSchedulingQuotaRules {
  overtimeShiftsPerWeek: number
  bathingShiftsPerWeek: number
  onCallEveryNDays: number
}

export interface BackendSchedulingSoftWeights {
  fairness: number | string
  preference: number | string
  weekend: number | string
  heavyShift: number | string
  workHourBalance: number | string
}

export interface BackendSchedulingRulesResponse {
  hardRules: BackendSchedulingHardRules
  quotas: BackendSchedulingQuotaRules
  softWeights: BackendSchedulingSoftWeights
}

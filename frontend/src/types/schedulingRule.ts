export interface SchedulingHardRules {
  minRestHoursBetweenShifts: number
  maxWorkHoursPerDay: number
  maxShiftsPerWeek: number
}

export interface SchedulingQuotaRules {
  overtimeShiftsPerWeek: number
  bathingShiftsPerWeek: number
  onCallEveryNDays: number
}

export interface SchedulingSoftWeights {
  fairness: number
  preference: number
  weekend: number
  heavyShift: number
  workHourBalance: number
}

export interface SchedulingRules {
  hardRules: SchedulingHardRules
  quotas: SchedulingQuotaRules
  softWeights: SchedulingSoftWeights
}

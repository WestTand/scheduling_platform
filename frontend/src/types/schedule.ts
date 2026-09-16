export const ScheduleStatus = {
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  PUBLISHED: 'PUBLISHED',
} as const

export type ScheduleStatus = (typeof ScheduleStatus)[keyof typeof ScheduleStatus]

export const ScheduleViewMode = {
  WEEK: 'WEEK',
  DAY: 'DAY',
  LIST: 'LIST',
} as const

export type ScheduleViewMode = (typeof ScheduleViewMode)[keyof typeof ScheduleViewMode]

export interface WorkSchedule {
  id: string
  name: string
  startDate: string
  endDate: string
  createdBy: string
  createdAt: string
  employeeCount: number
  assignmentCount: number
  shiftCount: number
  status: ScheduleStatus
  isMock: boolean
}

export interface ScheduleAssignment {
  id: string
  scheduleId: string
  date: string
  shiftCode: string
  shiftName: string
  startTime: string
  endTime: string
  color: string
  employeeNames: string[]
  warningCount: number
}

export interface ScheduleWarning {
  id: string
  type: 'HARD_CONSTRAINT' | 'SOFT_CONSTRAINT' | 'INFO'
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  employeeName: string
  date: string
  shift: string
  content: string
  suggestion: string
}

export interface ScheduleEvaluation {
  totalPenalty: number
  fairnessPenalty: number
  consecutiveShiftPenalty: number
  weekendPenalty: number
  preferencePenalty: number
  workHourImbalance: number
  shiftCountGap: number
  workHourGap: number
  fulfilledPreferences: number
  weights: {
    fairness: number
    consecutiveShift: number
    weekend: number
    preference: number
    workHour: number
  }
}

export interface ScheduleDetail extends WorkSchedule {
  assignments: ScheduleAssignment[]
  warnings: ScheduleWarning[]
  evaluation: ScheduleEvaluation
}

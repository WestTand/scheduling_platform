import type { ShiftCode } from '@/configs/shiftDefinitions'

export const ScheduleStatus = {
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  PUBLISHED: 'PUBLISHED',
  CANCELLED: 'CANCELLED',
} as const

export type ScheduleStatus = (typeof ScheduleStatus)[keyof typeof ScheduleStatus]

export const ScheduleViewMode = {
  WEEK: 'WEEK',
  DAILY: 'DAILY',
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
  workShiftId?: string
  employeeId?: string
  date: string
  shiftCode: ShiftCode
  employeeNames: string[]
  warningCount: number
  locked?: boolean
}

export interface ScheduleWarning {
  id: string
  type: 'HARD_CONSTRAINT' | 'SOFT_CONSTRAINT' | 'INFO'
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  employeeName: string
  date: string
  shiftCode: ShiftCode | ''
  content: string
  suggestion: string
}

export interface ScheduleDetail extends WorkSchedule {
  employeePool: string[]
  assignments: ScheduleAssignment[]
  warnings: ScheduleWarning[]
}

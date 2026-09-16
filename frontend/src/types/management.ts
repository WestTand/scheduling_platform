export const EmployeeStatus = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
} as const

export type EmployeeStatus = (typeof EmployeeStatus)[keyof typeof EmployeeStatus]

export const ShiftTypeStatus = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
} as const

export type ShiftTypeStatus = (typeof ShiftTypeStatus)[keyof typeof ShiftTypeStatus]

export const LeaveRequestStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const

export type LeaveRequestStatus = (typeof LeaveRequestStatus)[keyof typeof LeaveRequestStatus]

export const PreferenceType = {
  PREFERRED: 'PREFERRED',
  AVOID: 'AVOID',
  PREFER_SHIFT: 'PREFER_SHIFT',
  AVOID_SHIFT: 'AVOID_SHIFT',
} as const

export type PreferenceType = (typeof PreferenceType)[keyof typeof PreferenceType]

export const PreferenceStatus = {
  PENDING: 'PENDING',
  REVIEWED: 'REVIEWED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const

export type PreferenceStatus = (typeof PreferenceStatus)[keyof typeof PreferenceStatus]

export interface Employee {
  id: string
  employeeCode: string
  fullName: string
  email: string
  phone: string
  role: 'MANAGER' | 'EMPLOYEE'
  status: EmployeeStatus
  joinDate: string
  note?: string
}

export interface ShiftType {
  id: string
  code: string
  name: string
  startTime: string
  endTime: string
  durationHours: number
  color: string
  minEmployees: number
  maxEmployees: number
  description: string
  status: ShiftTypeStatus
}

export interface LeaveRequest {
  id: string
  employeeName: string
  employeeEmail: string
  startDate: string
  endDate: string
  totalDays: number
  reason: string
  requestedAt: string
  status: LeaveRequestStatus
  reviewedBy?: string
  reviewedAt?: string
  managerNote?: string
}

export interface WorkPreference {
  id: string
  employeeName: string
  employeeEmail?: string
  month: string
  type: PreferenceType
  relatedShiftType?: string
  note: string
  priority: number
  submittedAt: string
  status: PreferenceStatus
  preferenceTypeLabel?: string
  targetLabel?: string
  managerNote?: string
  adminNote?: string
}

export interface WorkPreferenceDecision {
  status: 'APPROVED' | 'REJECTED'
  adminNote: string
}

export interface PaginatedResult<T> {
  data: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface EmployeeFormValues {
  employeeCode: string
  fullName: string
  email: string
  phone: string
  role: 'MANAGER' | 'EMPLOYEE'
  status: EmployeeStatus
  joinDate: string
  note?: string
}

export interface ShiftTypeFormValues {
  code: string
  name: string
  startTime: string
  endTime: string
  color: string
  minEmployees: number
  maxEmployees: number
  description: string
  status: ShiftTypeStatus
}

export interface LeaveRequestDecision {
  status: LeaveRequestStatus
  managerNote: string
}

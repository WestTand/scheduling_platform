import type { ShiftCode } from '@/configs/shiftDefinitions'

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
  CANCELLED: 'CANCELLED',
} as const

export type LeaveRequestStatus = (typeof LeaveRequestStatus)[keyof typeof LeaveRequestStatus]

export const SwapRequestStatus = {
  PENDING_TARGET: 'PENDING_TARGET',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
} as const

export type SwapRequestStatus = (typeof SwapRequestStatus)[keyof typeof SwapRequestStatus]

export const NotificationType = {
  SCHEDULE: 'SCHEDULE',
  LEAVE: 'LEAVE',
  PREFERENCE: 'PREFERENCE',
  SWAP: 'SWAP',
  GENERAL: 'GENERAL',
} as const

export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType]

export const NotificationPriority = {
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
} as const

export type NotificationPriority = (typeof NotificationPriority)[keyof typeof NotificationPriority]

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
  CANCELLED: 'CANCELLED',
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
  group: 'THONG_TAM' | 'NGOAI_GIO' | 'CA_TRUC'
  startTime: string
  endTime: string
  crossesMidnight: boolean
  breakStart?: string
  breakEnd?: string
  breakMinutes: number
  workMinutes: number
  applicableDayType: 'WEEKDAY' | 'WEEKEND_OR_HOLIDAY'
  color: string
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
  relatedShiftCode?: ShiftCode
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
  group: 'THONG_TAM' | 'NGOAI_GIO' | 'CA_TRUC'
  crossesMidnight: boolean
  breakStart?: string
  breakEnd?: string
  breakMinutes: number
  workMinutes: number
  applicableDayType: 'WEEKDAY' | 'WEEKEND_OR_HOLIDAY'
  color: string
  description: string
  status: ShiftTypeStatus
}

export interface LeaveRequestDecision {
  status: LeaveRequestStatus
  managerNote: string
}

export interface SwapRequest {
  id: string
  requesterEmployeeId?: string
  targetEmployeeId?: string
  requesterAssignmentId?: string
  targetAssignmentId?: string
  employeeName: string
  employeeEmail: string
  currentShiftCode?: ShiftCode
  currentShiftName?: string
  requestedShiftCode?: ShiftCode
  requestedShiftName?: string
  partnerName?: string
  requestedDate: string
  reason: string
  note?: string
  status: SwapRequestStatus
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  managerNote?: string
}

export interface SwapAssignmentOption {
  id: string
  workDate: string
  shiftName: string
  shiftStartTime: string
  shiftEndTime: string
  periodName: string
  periodStatus: string
}

export interface CoworkerOption {
  id: string
  employeeCode: string
  fullName: string
}

export interface NotificationItem {
  id: string
  title: string
  content: string
  type: NotificationType
  priority: NotificationPriority
  recipient: string
  recipientType: 'ALL' | 'SELECTED'
  createdAt: string
  isRead: boolean
  metadata?: string
}

export interface ActivityLog {
  id: string
  timestamp: string
  actorName: string
  actorRole: 'MANAGER' | 'EMPLOYEE'
  action: string
  target: string
  description: string
  status: 'SUCCESS' | 'FAILED' | 'INFO'
  ipAddress: string
}

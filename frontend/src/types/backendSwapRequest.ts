export type BackendSwapRequestStatus = 'PENDING_TARGET' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendEmployeeSummary {
  id: number
  employeeCode: string
  fullName: string
}

export interface BackendSwapRequestResponse {
  id: number
  requesterAssignmentId: number
  requesterWorkDate: string
  requesterEmployee: BackendEmployeeSummary
  targetAssignmentId: number | null
  targetEmployee: BackendEmployeeSummary
  reason: string | null
  status: BackendSwapRequestStatus
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
}

export interface BackendCreateSwapRequestRequest {
  requesterAssignmentId: number
  targetEmployeeId: number
  targetAssignmentId: number | null
  reason?: string
}

export interface BackendRespondSwapRequestRequest {
  accept: boolean
}

export interface BackendEmployeeScheduleItemResponse {
  assignmentId: number
  assignmentStatus: string
  assignedSource: string
  locked: boolean
  workDate: string
  shiftTypeId: number
  shiftTypeName: string
  shiftStartTime: string
  shiftEndTime: string
  startsAt: string
  endsAt: string
  workMinutes: number
  isTransitionShift: boolean
  periodId: number
  periodName: string
  periodStatus: string
}

export interface BackendCoworkerResponse {
  id: number
  employeeCode: string
  fullName: string
}

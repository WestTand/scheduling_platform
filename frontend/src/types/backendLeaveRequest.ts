export type BackendLeaveRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendAdminLeaveRequestResponse {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  startDate: string
  endDate: string
  reason: string
  status: BackendLeaveRequestStatus
  reviewNote: string | null
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
}

export interface BackendEmployeeLeaveRequestResponse {
  id: number
  startDate: string
  endDate: string
  reason: string
  status: BackendLeaveRequestStatus
}

export interface BackendCreateLeaveRequestRequest {
  startDate: string
  endDate: string
  reason: string
}

export interface BackendReviewLeaveRequestRequest {
  reviewNote?: string
}

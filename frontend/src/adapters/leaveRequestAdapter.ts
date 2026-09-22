import type { BackendAdminLeaveRequestResponse, BackendEmployeeLeaveRequestResponse } from '@/types/backendLeaveRequest'
import type { LeaveRequest } from '@/types/management'

export const inclusiveDays = (startDate: string, endDate: string) => {
  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const diff = end.getTime() - start.getTime()
  return Math.max(1, Math.floor(diff / 86_400_000) + 1)
}

const employeeStatusNote = (status: BackendEmployeeLeaveRequestResponse['status']) => {
  if (status === 'PENDING') return 'Yêu cầu đang chờ quản lý duyệt.'
  if (status === 'APPROVED') return 'Yêu cầu đã được duyệt.'
  if (status === 'REJECTED') return 'Yêu cầu đã bị từ chối.'
  return 'Yêu cầu đã được hủy.'
}

export const adaptBackendAdminLeaveRequest = (request: BackendAdminLeaveRequestResponse): LeaveRequest => ({
  id: String(request.id),
  employeeName: request.employeeName,
  employeeEmail: '',
  startDate: request.startDate,
  endDate: request.endDate,
  totalDays: inclusiveDays(request.startDate, request.endDate),
  reason: request.reason,
  requestedAt: request.startDate,
  status: request.status,
  reviewedBy: request.reviewedByUsername ?? undefined,
  reviewedAt: request.reviewedAt ?? undefined,
  managerNote: request.reviewNote ?? undefined,
})

export const adaptBackendEmployeeLeaveRequest = (request: BackendEmployeeLeaveRequestResponse): LeaveRequest => ({
  id: String(request.id),
  employeeName: 'Bạn',
  employeeEmail: '',
  startDate: request.startDate,
  endDate: request.endDate,
  totalDays: inclusiveDays(request.startDate, request.endDate),
  reason: request.reason,
  requestedAt: request.startDate,
  status: request.status,
  managerNote: employeeStatusNote(request.status),
})

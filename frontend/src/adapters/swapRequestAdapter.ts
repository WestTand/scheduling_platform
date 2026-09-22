import type { BackendCoworkerResponse, BackendEmployeeScheduleItemResponse, BackendSwapRequestResponse } from '@/types/backendSwapRequest'
import type { CoworkerOption, SwapAssignmentOption, SwapRequest } from '@/types/management'

const fallbackDate = (request: BackendSwapRequestResponse) => request.reviewedAt?.slice(0, 10) ?? new Date().toISOString().slice(0, 10)

export const adaptBackendSwapRequest = (request: BackendSwapRequestResponse): SwapRequest => ({
  id: String(request.id),
  requesterEmployeeId: String(request.requesterEmployee.id),
  targetEmployeeId: String(request.targetEmployee.id),
  requesterAssignmentId: String(request.requesterAssignmentId),
  targetAssignmentId: request.targetAssignmentId == null ? undefined : String(request.targetAssignmentId),
  employeeName: request.requesterEmployee.fullName,
  employeeEmail: '',
  currentShiftName: `Assignment #${request.requesterAssignmentId}`,
  requestedShiftName: request.targetAssignmentId == null ? 'Nhờ nhận ca' : `Assignment #${request.targetAssignmentId}`,
  partnerName: request.targetEmployee.fullName,
  requestedDate: fallbackDate(request),
  reason: request.reason ?? '',
  note: request.status === 'PENDING_TARGET' ? 'Chờ nhân viên được đề nghị phản hồi.' : request.status === 'PENDING_APPROVAL' ? 'Chờ quản lý duyệt.' : undefined,
  status: request.status,
  submittedAt: fallbackDate(request),
  reviewedBy: request.reviewedByUsername ?? undefined,
  reviewedAt: request.reviewedAt ?? undefined,
})

export const adaptBackendScheduleAssignment = (item: BackendEmployeeScheduleItemResponse): SwapAssignmentOption => ({
  id: String(item.assignmentId),
  workDate: item.workDate,
  shiftName: item.shiftTypeName,
  shiftStartTime: item.shiftStartTime,
  shiftEndTime: item.shiftEndTime,
  periodName: item.periodName,
  periodStatus: item.periodStatus,
})

export const adaptBackendCoworker = (coworker: BackendCoworkerResponse): CoworkerOption => ({
  id: String(coworker.id),
  employeeCode: coworker.employeeCode,
  fullName: coworker.fullName,
})

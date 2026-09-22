import { adaptBackendAdminLeaveRequest, adaptBackendEmployeeLeaveRequest } from '@/adapters/leaveRequestAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type {
  BackendAdminLeaveRequestResponse,
  BackendCreateLeaveRequestRequest,
  BackendEmployeeLeaveRequestResponse,
} from '@/types/backendLeaveRequest'
import type { LeaveRequest, LeaveRequestDecision, PaginatedResult } from '@/types/management'

interface AdminListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
  startDate?: string
  endDate?: string
}

interface EmployeeListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
  startDate?: string
  endDate?: string
}

const paginate = (items: LeaveRequest[], page: number, pageSize: number): PaginatedResult<LeaveRequest> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterSearch = (items: LeaveRequest[], search: string) => {
  const normalized = search.trim().toLowerCase()
  if (!normalized) return items
  return items.filter((request) =>
    [request.employeeName, request.reason].some((value) => value.toLowerCase().includes(normalized)),
  )
}

const addDateParams = (params: URLSearchParams, startDate?: string, endDate?: string) => {
  if (startDate) params.set('from', startDate)
  if (endDate) params.set('to', endDate)
}

export const backendLeaveRequestService = {
  async listForManager({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: AdminListParams = {}) {
    const params = new URLSearchParams()
    if (status !== 'ALL') params.set('status', status)
    addDateParams(params, startDate, endDate)
    const query = params.toString()
    const response = await requestJson<BackendAdminLeaveRequestResponse[]>(`/api/admin/leave-requests${query ? `?${query}` : ''}`)
    return paginate(filterSearch(response.map(adaptBackendAdminLeaveRequest), search), page, pageSize)
  },

  async updateStatus(id: string, decision: LeaveRequestDecision) {
    const action = decision.status === 'APPROVED' ? 'approve' : 'reject'
    const response = await requestJson<BackendAdminLeaveRequestResponse>(`/api/admin/leave-requests/${encodeURIComponent(id)}/${action}`, {
      method: 'POST',
      body: { reviewNote: decision.managerNote.trim() || undefined },
    })
    return adaptBackendAdminLeaveRequest(response)
  },

  async listForEmployee({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: EmployeeListParams = {}) {
    const params = new URLSearchParams()
    if (status !== 'ALL') params.set('status', status)
    addDateParams(params, startDate, endDate)
    const query = params.toString()
    const response = await requestJson<BackendEmployeeLeaveRequestResponse[]>(`/api/employee/leave-requests${query ? `?${query}` : ''}`)
    return paginate(filterSearch(response.map(adaptBackendEmployeeLeaveRequest), search), page, pageSize)
  },

  async createForEmployee(input: BackendCreateLeaveRequestRequest) {
    const response = await requestJson<BackendEmployeeLeaveRequestResponse>('/api/employee/leave-requests', {
      method: 'POST',
      body: input,
    })
    return adaptBackendEmployeeLeaveRequest(response)
  },

  async cancelForEmployee(id: string) {
    await requestVoid(`/api/employee/leave-requests/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
  },
}

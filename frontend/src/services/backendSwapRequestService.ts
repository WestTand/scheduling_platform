import { adaptBackendCoworker, adaptBackendScheduleAssignment, adaptBackendSwapRequest } from '@/adapters/swapRequestAdapter'
import { requestJson } from '@/services/apiClient'
import type {
  BackendCoworkerResponse,
  BackendCreateSwapRequestRequest,
  BackendEmployeeScheduleItemResponse,
  BackendRespondSwapRequestRequest,
  BackendSwapRequestResponse,
} from '@/types/backendSwapRequest'
import type { CoworkerOption, SwapAssignmentOption, SwapRequest, SwapRequestStatus } from '@/types/management'

interface ListParams { status?: string }
interface CreateEmployeeSwapInput { requesterAssignmentId: number; targetEmployeeId: number; reason: string }

const buildStatusQuery = (status?: string) => {
  const params = new URLSearchParams()
  if (status && status !== 'ALL') params.set('status', status)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const backendSwapRequestService = {
  async listForManager({ status = 'ALL' }: ListParams = {}): Promise<SwapRequest[]> {
    const response = await requestJson<BackendSwapRequestResponse[]>(`/api/admin/swap-requests${buildStatusQuery(status)}`)
    return response.map(adaptBackendSwapRequest)
  },

  async listForEmployee({ status = 'ALL' }: ListParams = {}): Promise<SwapRequest[]> {
    const response = await requestJson<BackendSwapRequestResponse[]>(`/api/employee/swap-requests${buildStatusQuery(status)}`)
    return response.map(adaptBackendSwapRequest)
  },

  async createForEmployee(input: CreateEmployeeSwapInput): Promise<SwapRequest> {
    const body: BackendCreateSwapRequestRequest = {
      requesterAssignmentId: input.requesterAssignmentId,
      targetEmployeeId: input.targetEmployeeId,
      targetAssignmentId: null,
      reason: input.reason.trim() || undefined,
    }
    const response = await requestJson<BackendSwapRequestResponse>('/api/employee/swap-requests', { method: 'POST', body })
    return adaptBackendSwapRequest(response)
  },

  async cancelForEmployee(id: string): Promise<SwapRequest> {
    const response = await requestJson<BackendSwapRequestResponse>(`/api/employee/swap-requests/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
    return adaptBackendSwapRequest(response)
  },

  async respondForEmployee(id: string, accept: boolean, targetAssignmentId?: number): Promise<SwapRequest> {
    const body: BackendRespondSwapRequestRequest = accept
      ? { accept: true, targetAssignmentId }
      : { accept: false }
    const response = await requestJson<BackendSwapRequestResponse>(`/api/employee/swap-requests/${encodeURIComponent(id)}/respond`, { method: 'POST', body })
    return adaptBackendSwapRequest(response)
  },

  async updateStatus(id: string, status: SwapRequestStatus): Promise<SwapRequest> {
    const action = status === 'APPROVED' ? 'approve' : 'reject'
    const response = await requestJson<BackendSwapRequestResponse>(`/api/admin/swap-requests/${encodeURIComponent(id)}/${action}`, { method: 'POST' })
    return adaptBackendSwapRequest(response)
  },

  async listMyAssignments(): Promise<SwapAssignmentOption[]> {
    const response = await requestJson<BackendEmployeeScheduleItemResponse[]>('/api/employee/schedule')
    return response.map(adaptBackendScheduleAssignment)
  },

  async listCoworkers(): Promise<CoworkerOption[]> {
    const response = await requestJson<BackendCoworkerResponse[]>('/api/employee/coworkers')
    return response.map(adaptBackendCoworker)
  },
}

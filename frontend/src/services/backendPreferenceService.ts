import { adaptBackendPreference, toBackendPreferenceType } from '@/adapters/preferenceAdapter'
import { requestJson } from '@/services/apiClient'
import type {
  BackendCreatePreferenceRequest,
  BackendPreferenceResponse,
  BackendPreferenceType,
  BackendUpdatePreferenceRequest,
} from '@/types/backendPreference'
import type { PreferenceType, WorkPreference } from '@/types/management'

interface ListParams {
  status?: string
  from?: string
  to?: string
}

export interface CreateEmployeePreferenceInput {
  preferenceDate: string
  preferenceType: PreferenceType | BackendPreferenceType
  shiftTypeId?: number | null
  priority: number
  note?: string
}

export interface UpdateEmployeePreferenceInput {
  preferenceDate: string
  preferenceType: PreferenceType | BackendPreferenceType
  shiftTypeId?: number | null
  priority: number
  note?: string
}

const resolveBackendType = (type: PreferenceType | BackendPreferenceType): BackendPreferenceType => {
  if (type === 'WANT' || type === 'AVOID') {
    return type
  }
  return toBackendPreferenceType(type as PreferenceType)
}

const buildListQuery = ({ status, from, to }: ListParams = {}) => {
  const params = new URLSearchParams()
  if (status && status !== 'ALL') params.set('status', status)
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return query ? `?${query}` : ''
}

const toBackendInput = (input: CreateEmployeePreferenceInput): BackendCreatePreferenceRequest => ({
  preferenceDate: input.preferenceDate,
  preferenceType: resolveBackendType(input.preferenceType),
  shiftTypeId: input.shiftTypeId ?? undefined,
  priority: input.priority,
  note: input.note?.trim() ? input.note.trim() : undefined,
})

export const backendPreferenceService = {
  async listForManager(params: ListParams = {}): Promise<WorkPreference[]> {
    const response = await requestJson<BackendPreferenceResponse[]>(`/api/admin/preferences${buildListQuery(params)}`)
    return response.map(adaptBackendPreference)
  },

  async approveForManager(id: string): Promise<WorkPreference> {
    const response = await requestJson<BackendPreferenceResponse>(`/api/admin/preferences/${encodeURIComponent(id)}/approve`, {
      method: 'POST',
    })
    return adaptBackendPreference(response)
  },

  async rejectForManager(id: string): Promise<WorkPreference> {
    const response = await requestJson<BackendPreferenceResponse>(`/api/admin/preferences/${encodeURIComponent(id)}/reject`, {
      method: 'POST',
    })
    return adaptBackendPreference(response)
  },

  async listForEmployee(params: ListParams = {}): Promise<WorkPreference[]> {
    const response = await requestJson<BackendPreferenceResponse[]>(`/api/employee/preferences${buildListQuery(params)}`)
    return response.map(adaptBackendPreference)
  },

  async createForEmployee(input: CreateEmployeePreferenceInput): Promise<WorkPreference> {
    const body: BackendCreatePreferenceRequest = toBackendInput(input)
    const response = await requestJson<BackendPreferenceResponse>('/api/employee/preferences', { method: 'POST', body })
    return adaptBackendPreference(response)
  },

  async updateForEmployee(id: string, input: UpdateEmployeePreferenceInput): Promise<WorkPreference> {
    const body: BackendUpdatePreferenceRequest = toBackendInput(input)
    const response = await requestJson<BackendPreferenceResponse>(`/api/employee/preferences/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body,
    })
    return adaptBackendPreference(response)
  },

  async cancelForEmployee(id: string): Promise<void> {
    await requestJson<void>(`/api/employee/preferences/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
  },
}

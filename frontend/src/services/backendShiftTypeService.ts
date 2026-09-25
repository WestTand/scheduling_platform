import { requestJson } from '@/services/apiClient'
import { adaptBackendShiftType, toBackendCreateShiftTypeRequest, toBackendUpdateShiftTypeRequest } from '@/adapters/shiftTypeAdapter'
import type { BackendShiftTypeResponse } from '@/types/backendShiftType'
import type { PaginatedResult, ShiftType, ShiftTypeFormValues } from '@/types/management'

const pageResult = <T>(items: T[], page: number, pageSize: number): PaginatedResult<T> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

export const backendShiftTypeService = {
  async listActive() {
    return requestJson<BackendShiftTypeResponse[]>('/api/admin/shift-types?status=ACTIVE')
  },

  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string }) {
    const params = new URLSearchParams()
    if (search.trim()) params.set('query', search.trim())
    if (status !== 'ALL') params.set('status', status)
    const query = params.toString()
    const response = await requestJson<BackendShiftTypeResponse[]>(`/api/admin/shift-types${query ? `?${query}` : ''}`)
    return pageResult(response.map(adaptBackendShiftType), page, pageSize)
  },

  async getById(id: string): Promise<ShiftType | null> {
    const items = await requestJson<BackendShiftTypeResponse[]>('/api/admin/shift-types')
    const found = items.find((item) => String(item.id) === id)
    return found ? adaptBackendShiftType(found) : null
  },

  async add(data: ShiftTypeFormValues): Promise<ShiftType> {
    return adaptBackendShiftType(await requestJson<BackendShiftTypeResponse>('/api/admin/shift-types', { method: 'POST', body: toBackendCreateShiftTypeRequest(data) }))
  },

  async update(id: string, data: ShiftTypeFormValues): Promise<ShiftType> {
    return adaptBackendShiftType(await requestJson<BackendShiftTypeResponse>(`/api/admin/shift-types/${encodeURIComponent(id)}`, { method: 'PUT', body: toBackendUpdateShiftTypeRequest(data) }))
  },

  async toggleStatus(id: string): Promise<ShiftType> {
    const current = await this.getById(id)
    if (!current) throw new Error('Loại ca trực không tồn tại.')
    const action = current.status === 'ACTIVE' ? 'lock' : 'unlock'
    await requestJson<void>(`/api/admin/shift-types/${encodeURIComponent(id)}/${action}`, { method: 'POST' })
    return { ...current, status: current.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }
  },

  async remove(id: string): Promise<ShiftType> {
    return this.toggleStatus(id)
  },
}

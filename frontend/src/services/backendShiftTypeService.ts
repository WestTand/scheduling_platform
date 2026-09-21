import { adaptBackendShiftType, toBackendCreateShiftTypeRequest, toBackendUpdateShiftTypeRequest } from '@/adapters/shiftTypeAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { BackendShiftTypeResponse } from '@/types/backendShiftType'
import type { PaginatedResult, ShiftType, ShiftTypeFormValues } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  status?: string
}

function paginate(items: ShiftType[], page: number, pageSize: number): PaginatedResult<ShiftType> {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

function shiftTypePath(id: string): string {
  return `/api/admin/shift-types/${encodeURIComponent(id)}`
}

export const backendShiftTypeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: ListParams = {}): Promise<PaginatedResult<ShiftType>> {
    const params = new URLSearchParams()
    if (search.trim()) params.set('query', search.trim())
    if (status !== 'ALL') params.set('status', status)
    const query = params.toString()
    const response = await requestJson<BackendShiftTypeResponse[]>(`/api/admin/shift-types${query ? `?${query}` : ''}`)
    return paginate(response.map(adaptBackendShiftType), page, pageSize)
  },

  async getById(id: string): Promise<ShiftType | null> {
    const result = await backendShiftTypeService.list({ page: 1, pageSize: Number.MAX_SAFE_INTEGER })
    return result.data.find((shiftType) => shiftType.id === id) ?? null
  },

  async add(data: ShiftTypeFormValues): Promise<ShiftType> {
    const response = await requestJson<BackendShiftTypeResponse>('/api/admin/shift-types', {
      method: 'POST',
      body: toBackendCreateShiftTypeRequest(data),
    })
    return adaptBackendShiftType(response)
  },

  async update(id: string, data: ShiftTypeFormValues): Promise<ShiftType> {
    const response = await requestJson<BackendShiftTypeResponse>(shiftTypePath(id), {
      method: 'PUT',
      body: toBackendUpdateShiftTypeRequest(data),
    })
    return adaptBackendShiftType(response)
  },

  async toggleStatus(id: string): Promise<ShiftType> {
    const current = await backendShiftTypeService.getById(id)
    if (!current) {
      throw new Error('Loại ca trực không tồn tại.')
    }
    if (current.status === 'ACTIVE') {
      await requestVoid(`${shiftTypePath(id)}/lock`, { method: 'POST' })
    } else {
      await requestVoid(`${shiftTypePath(id)}/unlock`, { method: 'POST' })
    }
    const updated = await backendShiftTypeService.getById(id)
    if (!updated) {
      throw new Error('Không thể tải lại loại ca trực sau khi đổi trạng thái.')
    }
    return updated
  },

  async remove(id: string): Promise<ShiftType> {
    await requestVoid(`${shiftTypePath(id)}/lock`, { method: 'POST' })
    const updated = await backendShiftTypeService.getById(id)
    if (!updated) {
      throw new Error('Không thể tải lại loại ca trực sau khi tạm ngưng.')
    }
    return updated
  },
}

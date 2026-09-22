import { apiConfig } from '@/config/api'
import { preferenceSeed } from '@/mocks/managementData'
import { backendPreferenceService } from '@/services/backendPreferenceService'
import type { PaginatedResult, PreferenceStatus, PreferenceType, WorkPreference, WorkPreferenceDecision } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let preferences: WorkPreference[] = clone(preferenceSeed)

const paginate = (items: WorkPreference[], page: number, pageSize: number): PaginatedResult<WorkPreference> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize

  return {
    data: items.slice(startIndex, startIndex + pageSize),
    page: safePage,
    pageSize,
    total,
    totalPages,
  }
}

const filterPreferences = (items: WorkPreference[], search: string, month: string, type: string, status: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items.filter((preference) => {
    const matchesSearch =
      !normalizedSearch ||
      [preference.employeeName, preference.note ?? '', preference.targetLabel ?? '', preference.relatedShiftCode ?? ''].some((value) =>
        value.toLowerCase().includes(normalizedSearch),
      )
    const matchesMonth = month === 'ALL' || preference.month === month
    const matchesType = type === 'ALL' || preference.type === type
    const matchesStatus = status === 'ALL' || preference.status === status

    return matchesSearch && matchesMonth && matchesType && matchesStatus
  })
}

export const preferenceService = {
  async list({ page = 1, pageSize = 5, search = '', month = 'ALL', type = 'ALL', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; month?: string; type?: string; status?: string }) {
    if (!apiConfig.useMockApi) {
      const response = await backendPreferenceService.listForManager({ status })
      return paginate(filterPreferences(response, search, month, type, 'ALL'), page, pageSize)
    }
    await delay(350)

    const filtered = preferences.filter((preference) => {
      const matchesSearch = !search || preference.employeeName.toLowerCase().includes(search.toLowerCase())
      const matchesMonth = month === 'ALL' || preference.month === month
      const matchesType = type === 'ALL' || preference.type === type
      const matchesStatus = status === 'ALL' || preference.status === status

      return matchesSearch && matchesMonth && matchesType && matchesStatus
    })

    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / pageSize))
    const safePage = Math.min(page, totalPages)
    const startIndex = (safePage - 1) * pageSize

    return {
      data: filtered.slice(startIndex, startIndex + pageSize),
      page: safePage,
      pageSize,
      total,
      totalPages,
    } satisfies PaginatedResult<WorkPreference>
  },

  async getById(id: string) {
    await delay(200)
    return preferences.find((preference) => preference.id === id) ?? null
  },

  async review(id: string, status: PreferenceStatus, managerNote: string) {
    await delay(300)
    const preference = preferences.find((item) => item.id === id)

    if (!preference) {
      throw new Error('Nguyện vọng không tồn tại.')
    }

    preference.status = status
    preference.managerNote = managerNote || undefined

    return preference
  },

  async updateStatus(id: string, decision: WorkPreferenceDecision) {
    if (!apiConfig.useMockApi) {
      return decision.status === 'APPROVED' ? backendPreferenceService.approveForManager(id) : backendPreferenceService.rejectForManager(id)
    }
    await delay(300)
    const preference = preferences.find((item) => item.id === id)

    if (!preference) {
      throw new Error('Nguyện vọng không tồn tại.')
    }

    preference.status = decision.status === 'APPROVED' ? 'APPROVED' : 'REJECTED'
    preference.managerNote = decision.adminNote
    preference.adminNote = decision.adminNote

    return preference
  },

  reset() {
    preferences = clone(preferenceSeed)
  },
}

export const preferenceTypes: Array<{ value: PreferenceType; label: string }> = [
  { value: 'PREFERRED', label: 'Muốn làm' },
  { value: 'AVOID', label: 'Không muốn làm' },
  { value: 'PREFER_SHIFT', label: 'Ưu tiên ca' },
  { value: 'AVOID_SHIFT', label: 'Tránh ca' },
]

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

const filterPreferences = (items: WorkPreference[], search: string, type: string, status: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items
    .filter((preference) => {
      const matchesSearch =
        !normalizedSearch ||
        [preference.employeeName, preference.note ?? '', preference.targetLabel ?? '', preference.relatedShiftCode ?? ''].some((value) =>
          value.toLowerCase().includes(normalizedSearch),
        )
      const matchesType = type === 'ALL' || preference.type === type
      const matchesStatus = status === 'ALL' || preference.status === status

      return matchesSearch && matchesType && matchesStatus
    })
    .sort((a, b) => (b.preferenceDate > a.preferenceDate ? 1 : b.preferenceDate < a.preferenceDate ? -1 : 0))
}

export const preferenceService = {
  async list({ page = 1, pageSize = 5, search = '', type = 'ALL', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; type?: string; status?: string }) {
    if (!apiConfig.useMockApi) {
      const response = await backendPreferenceService.listForManager({ status })
      // Backend adapter leaves employeeName empty, has no relatedShiftCode, and collapses
      // type to PREFERRED|AVOID, so skip the degraded local type filter here and only
      // search present fields (note/targetLabel/employeeName) before paginating.
      return paginate(filterPreferences(response, search, 'ALL', 'ALL'), page, pageSize)
    }
    await delay(350)

    const filtered = preferences
      .filter((preference) => {
        const matchesSearch = !search || preference.employeeName.toLowerCase().includes(search.toLowerCase())
        const matchesType = type === 'ALL' || preference.type === type
        const matchesStatus = status === 'ALL' || preference.status === status

        return matchesSearch && matchesType && matchesStatus
      })
      .sort((a, b) => (b.preferenceDate > a.preferenceDate ? 1 : b.preferenceDate < a.preferenceDate ? -1 : 0))

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
    if (!apiConfig.useMockApi) {
      const response = await backendPreferenceService.listForManager({})
      return response.find((preference) => preference.id === id) ?? null
    }
    await delay(200)
    return preferences.find((preference) => preference.id === id) ?? null
  },

  async review(id: string, status: PreferenceStatus, managerNote: string) {
    if (!apiConfig.useMockApi) {
      return status === 'APPROVED' ? backendPreferenceService.approveForManager(id) : backendPreferenceService.rejectForManager(id)
    }
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

  async listMine({ page = 1, pageSize = 50, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string } = {}) {
    if (!apiConfig.useMockApi) {
      const response = await backendPreferenceService.listForEmployee({ status })
      return paginate(filterPreferences(response, search, 'ALL', 'ALL'), page, pageSize)
    }
    return this.list({ page, pageSize, search, status })
  },

  async createMine(input: { preferenceDate: string; type: PreferenceType; shiftTypeId?: number | null; relatedShiftCode?: string; priority: number; note: string }) {
    if (!apiConfig.useMockApi) {
      return backendPreferenceService.createForEmployee({
        preferenceDate: input.preferenceDate,
        preferenceType: input.type,
        shiftTypeId: input.shiftTypeId ?? null,
        shiftCode: input.relatedShiftCode ?? undefined,
        priority: input.priority,
        note: input.note,
      })
    }
    await delay(300)
    const created: WorkPreference = {
      id: `PREF-${Date.now()}`,
      employeeName: 'Bạn',
      preferenceDate: input.preferenceDate,
      type: input.type,
      note: input.note,
      priority: input.priority,
      submittedAt: new Date().toISOString(),
      status: 'PENDING',
      relatedShiftCode: (input.relatedShiftCode as WorkPreference['relatedShiftCode'] | undefined) ?? undefined,
      targetLabel: input.relatedShiftCode ?? undefined,
    }
    preferences = [created, ...preferences]
    return created
  },

  async updateMine(id: string, input: { preferenceDate: string; type: PreferenceType; shiftTypeId?: number | null; relatedShiftCode?: string; priority: number; note: string }) {
    if (!apiConfig.useMockApi) {
      return backendPreferenceService.updateForEmployee(id, {
        preferenceDate: input.preferenceDate,
        preferenceType: input.type,
        shiftTypeId: input.shiftTypeId ?? null,
        shiftCode: input.relatedShiftCode ?? undefined,
        priority: input.priority,
        note: input.note,
      })
    }
    await delay(300)
    const preference = preferences.find((item) => item.id === id)

    if (!preference) {
      throw new Error('Nguyện vọng không tồn tại.')
    }

    preference.preferenceDate = input.preferenceDate
    preference.type = input.type
    preference.note = input.note
    preference.priority = input.priority
    preference.relatedShiftCode = (input.relatedShiftCode as WorkPreference['relatedShiftCode'] | undefined) ?? preference.relatedShiftCode
    preference.targetLabel = input.relatedShiftCode ?? preference.targetLabel

    return preference
  },

  async cancelMine(id: string) {
    if (!apiConfig.useMockApi) {
      await backendPreferenceService.cancelForEmployee(id)
      return
    }
    await delay(200)
    preferences = preferences.filter((item) => item.id !== id)
  },

  reset() {
    preferences = clone(preferenceSeed)
  },
}

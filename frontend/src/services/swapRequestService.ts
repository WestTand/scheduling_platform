import { swapRequestSeed } from '@/mocks/managementData'
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import type { PaginatedResult, SwapRequest, SwapRequestStatus } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let swapRequests: SwapRequest[] = clone(swapRequestSeed)

export const swapRequestService = {
  async list({
    page = 1,
    pageSize = 5,
    search = '',
    status = 'ALL',
    startDate = '',
    endDate = '',
  }: {
    page?: number
    pageSize?: number
    search?: string
    status?: string
    startDate?: string
    endDate?: string
  }) {
    await delay(300)

    const filtered = swapRequests.filter((request) => {
      const matchesSearch = !search || [request.employeeName, request.currentShiftCode, request.requestedShiftCode, getShiftDefinition(request.currentShiftCode)?.name ?? '', getShiftDefinition(request.requestedShiftCode)?.name ?? ''].some((value) => value.toLowerCase().includes(search.toLowerCase()))
      const matchesStatus = status === 'ALL' || request.status === status
      const matchesStartDate = !startDate || request.requestedDate >= startDate
      const matchesEndDate = !endDate || request.requestedDate <= endDate

      return matchesSearch && matchesStatus && matchesStartDate && matchesEndDate
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
    } satisfies PaginatedResult<SwapRequest>
  },

  async getById(id: string) {
    await delay(200)
    return swapRequests.find((request) => request.id === id) ?? null
  },

  async updateStatus(id: string, status: SwapRequestStatus, managerNote: string) {
    await delay(250)
    const request = swapRequests.find((item) => item.id === id)

    if (!request) {
      throw new Error('Yêu cầu đổi ca không tồn tại.')
    }

    if (request.status !== 'PENDING') {
      throw new Error('Yêu cầu đã được xử lý, không thể cập nhật lại.')
    }

    request.status = status
    request.reviewedBy = 'Nguyễn Thị Lan'
    request.reviewedAt = new Date().toISOString()
    request.managerNote = managerNote || undefined

    return request
  },

  async create(data: Omit<SwapRequest, 'id' | 'status' | 'submittedAt' | 'reviewedAt' | 'reviewedBy' | 'managerNote'> & { status?: SwapRequestStatus }) {
    await delay(250)

    const newRequest: SwapRequest = {
      ...data,
      id: `SWAP-${Date.now()}`,
      status: data.status ?? 'PENDING',
      submittedAt: new Date().toISOString(),
      reviewedAt: undefined,
      reviewedBy: undefined,
      managerNote: undefined,
    }

    swapRequests = [newRequest, ...swapRequests]
    return newRequest
  },

  async cancel(id: string) {
    await delay(200)
    const request = swapRequests.find((item) => item.id === id)

    if (!request) {
      throw new Error('Yêu cầu đổi ca không tồn tại.')
    }

    if (request.status !== 'PENDING') {
      throw new Error('Chỉ có yêu cầu đang chờ xử lý mới được hủy.')
    }

    request.status = 'CANCELLED'
    return request
  },

  reset() {
    swapRequests = clone(swapRequestSeed)
  },
}

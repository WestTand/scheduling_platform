import { leaveRequestSeed } from '@/mocks/managementData'
import type { LeaveRequest, LeaveRequestDecision, PaginatedResult } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let leaveRequests: LeaveRequest[] = clone(leaveRequestSeed)

export const leaveRequestService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string }) {
    await delay(350)

    const filtered = leaveRequests.filter((request) => {
      const matchesSearch = !search || request.employeeName.toLowerCase().includes(search.toLowerCase())
      const matchesStatus = status === 'ALL' || request.status === status
      const matchesStartDate = !startDate || request.startDate >= startDate
      const matchesEndDate = !endDate || request.endDate <= endDate

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
    } satisfies PaginatedResult<LeaveRequest>
  },

  async getById(id: string) {
    await delay(200)
    return leaveRequests.find((request) => request.id === id) ?? null
  },

  async updateStatus(id: string, decision: LeaveRequestDecision) {
    await delay(300)
    const request = leaveRequests.find((item) => item.id === id)

    if (!request) {
      throw new Error('Yêu cầu nghỉ không tồn tại.')
    }

    if (request.status !== 'PENDING') {
      throw new Error('Yêu cầu đã được xử lý, không thể duyệt hoặc từ chối lần nữa.')
    }

    request.status = decision.status
    request.reviewedBy = 'Nguyễn Thị Lan'
    request.reviewedAt = new Date().toISOString()
    request.managerNote = decision.managerNote

    return request
  },

  reset() {
    leaveRequests = clone(leaveRequestSeed)
  },
}

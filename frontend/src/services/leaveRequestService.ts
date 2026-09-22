import { apiConfig } from '@/config/api'
import { backendLeaveRequestService } from '@/services/backendLeaveRequestService'
import { leaveRequestSeed } from '@/mocks/managementData'
import type { LeaveRequest, LeaveRequestDecision, PaginatedResult } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let leaveRequests: LeaveRequest[] = clone(leaveRequestSeed)

export const leaveRequestService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string }) {
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.listForManager({ page, pageSize, search, status, startDate, endDate })
    }
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
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.updateStatus(id, decision)
    }
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

  async listMine(params: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string } = {}) {
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.listForEmployee(params)
    }
    return this.list(params)
  },

  async createMine(input: { startDate: string; endDate: string; reason: string; note?: string; type?: string }) {
    if (!apiConfig.useMockApi) {
      return backendLeaveRequestService.createForEmployee({
        startDate: input.startDate,
        endDate: input.endDate,
        reason: input.reason.trim(),
      })
    }
    await delay(300)
    const newRequest: LeaveRequest = {
      id: `LEAVE-${Date.now()}`,
      employeeName: 'Bạn',
      employeeEmail: '',
      startDate: input.startDate,
      endDate: input.endDate,
      totalDays: Math.max(1, Math.floor((new Date(`${input.endDate}T00:00:00`).getTime() - new Date(`${input.startDate}T00:00:00`).getTime()) / 86_400_000) + 1),
      reason: input.reason.trim(),
      requestedAt: new Date().toISOString(),
      status: 'PENDING',
      managerNote: input.note || 'Yêu cầu đang chờ quản lý duyệt.',
    }
    leaveRequests = [newRequest, ...leaveRequests]
    return clone(newRequest)
  },

  async cancelMine(id: string) {
    if (!apiConfig.useMockApi) {
      await backendLeaveRequestService.cancelForEmployee(id)
      return
    }
    await delay(250)
    const request = leaveRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu nghỉ không tồn tại.')
    if (request.status !== 'PENDING') throw new Error('Chỉ có thể hủy yêu cầu đang chờ duyệt.')
    request.status = 'CANCELLED'
    request.managerNote = 'Nhân viên hủy yêu cầu.'
  },

  reset() {
    leaveRequests = clone(leaveRequestSeed)
  },
}

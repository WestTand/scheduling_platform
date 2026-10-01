import { apiConfig } from '@/config/api'
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import { swapRequestSeed } from '@/mocks/managementData'
import { backendSwapRequestService } from '@/services/backendSwapRequestService'
import type { CoworkerOption, PaginatedResult, SwapAssignmentOption, SwapRequest, SwapRequestStatus } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let swapRequests: SwapRequest[] = clone(swapRequestSeed).map((item) => ({
  ...item,
  status: (item.status as string) === 'PENDING' ? 'PENDING_APPROVAL' : item.status,
}))

const paginate = (items: SwapRequest[], page: number, pageSize: number): PaginatedResult<SwapRequest> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterRequests = (items: SwapRequest[], search: string, status: string, startDate: string, endDate: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items.filter((request) => {
    const currentShift = request.currentShiftCode ? getShiftDefinition(request.currentShiftCode)?.name ?? '' : request.currentShiftName ?? ''
    const requestedShift = request.requestedShiftCode ? getShiftDefinition(request.requestedShiftCode)?.name ?? '' : request.requestedShiftName ?? ''
    const matchesSearch = !normalizedSearch || [request.employeeName, request.partnerName ?? '', request.reason, currentShift, requestedShift].some((value) => value.toLowerCase().includes(normalizedSearch))
    const matchesStatus = status === 'ALL' || request.status === status
    const matchesStartDate = !startDate || request.requestedDate >= startDate
    const matchesEndDate = !endDate || request.requestedDate <= endDate
    return matchesSearch && matchesStatus && matchesStartDate && matchesEndDate
  })
}

export const swapRequestService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string }) {
    if (!apiConfig.useMockApi) {
      const response = await backendSwapRequestService.listForManager({ status })
      return paginate(filterRequests(response, search, 'ALL', startDate, endDate), page, pageSize)
    }
    await delay(300)
    return paginate(filterRequests(swapRequests, search, status, startDate, endDate), page, pageSize)
  },

  async listMine(params: { page?: number; pageSize?: number; search?: string; status?: string } = {}) {
    if (!apiConfig.useMockApi) {
      const response = await backendSwapRequestService.listForEmployee({ status: params.status ?? 'ALL' })
      return paginate(filterRequests(response, params.search ?? '', 'ALL', '', ''), params.page ?? 1, params.pageSize ?? 5)
    }
    return this.list({ ...params, status: params.status ?? 'ALL' })
  },

  async listMyAssignments(): Promise<SwapAssignmentOption[]> {
    if (!apiConfig.useMockApi) return backendSwapRequestService.listMyAssignments()
    await delay(150)
    return [
      { id: 'assignment-01', workDate: '2026-09-22', shiftName: 'Thông tầm', shiftStartTime: '08:00:00', shiftEndTime: '17:00:00', periodName: 'Mock period', periodStatus: 'PUBLISHED' },
    ]
  },

  async listCoworkers(): Promise<CoworkerOption[]> {
    if (!apiConfig.useMockApi) return backendSwapRequestService.listCoworkers()
    await delay(150)
    return [{ id: '9', employeeCode: 'EMP009', fullName: 'Trần Văn Nam' }]
  },

  async updateStatus(id: string, status: SwapRequestStatus, managerNote?: string) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.updateStatus(id, status)
    await delay(250)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (status === 'APPROVED' && request.status !== 'PENDING_APPROVAL') throw new Error('Yêu cầu chưa sẵn sàng để duyệt.')
    if (status === 'REJECTED' && request.status !== 'PENDING_TARGET' && request.status !== 'PENDING_APPROVAL') throw new Error('Yêu cầu đã được xử lý, không thể cập nhật lại.')
    request.status = status
    request.reviewedBy = 'Nguyễn Thị Lan'
    request.reviewedAt = new Date().toISOString()
    request.managerNote = managerNote || undefined
    return request
  },

  async createMine(input: { requesterAssignmentId: string; targetEmployeeId: string; reason: string }) {
    if (!apiConfig.useMockApi) {
      return backendSwapRequestService.createForEmployee({ requesterAssignmentId: Number(input.requesterAssignmentId), targetEmployeeId: Number(input.targetEmployeeId), reason: input.reason })
    }
    await delay(250)
    const newRequest: SwapRequest = { id: `SWAP-${Date.now()}`, requesterAssignmentId: input.requesterAssignmentId, targetEmployeeId: input.targetEmployeeId, employeeName: 'Bạn', employeeEmail: '', currentShiftName: `Assignment #${input.requesterAssignmentId}`, requestedShiftName: 'Nhờ nhận ca', partnerName: `Employee #${input.targetEmployeeId}`, requestedDate: new Date().toISOString().slice(0, 10), reason: input.reason, status: 'PENDING_TARGET', submittedAt: new Date().toISOString() }
    swapRequests = [newRequest, ...swapRequests]
    return newRequest
  },

  async cancelMine(id: string) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.cancelForEmployee(id)
    await delay(200)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (request.status !== 'PENDING_TARGET') throw new Error('Chỉ có yêu cầu đang chờ nhân viên phản hồi mới được hủy.')
    request.status = 'CANCELLED'
    return request
  },

  async respondMine(id: string, accept: boolean, targetAssignmentId?: number) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.respondForEmployee(id, accept, targetAssignmentId)
    await delay(200)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (request.status !== 'PENDING_TARGET') throw new Error('Yêu cầu không còn chờ phản hồi.')
    request.status = accept ? 'PENDING_APPROVAL' : 'REJECTED'
    request.targetAssignmentId = targetAssignmentId === undefined ? undefined : String(targetAssignmentId)
    return request
  },

  reset() {
    swapRequests = clone(swapRequestSeed).map((item) => ({ ...item, status: (item.status as string) === 'PENDING' ? 'PENDING_APPROVAL' : item.status }))
  },
}

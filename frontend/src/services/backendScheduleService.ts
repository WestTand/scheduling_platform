import { adaptBackendPeriodSchedule, adaptBackendSchedulePeriod, adaptBackendShiftAssignment } from '@/adapters/scheduleAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { BackendGenerateScheduleRequest, BackendGenerateScheduleResponse, BackendOptimizeScheduleResponse, BackendPeriodScheduleResponse, BackendSchedulePeriodResponse, BackendShiftAssignmentResponse, BackendWorkShiftResponse } from '@/types/backendSchedule'
import type { ScheduleDetail, ScheduleStatus, WorkSchedule } from '@/types/schedule'

interface ListPeriodParams {
  search?: string
  status?: 'ALL' | ScheduleStatus
  startDate?: string
  endDate?: string
}

interface CreatePeriodPayload {
  periodName: string
  startDate: string
  endDate: string
  versionNo: number
}

const filterSearch = (items: WorkSchedule[], search = '') => {
  const normalized = search.trim().toLowerCase()
  if (!normalized) return items
  return items.filter((item) => item.name.toLowerCase().includes(normalized))
}

const backendListableStatuses: ScheduleStatus[] = ['DRAFT', 'APPROVED', 'PUBLISHED', 'CANCELLED']

const listPeriodsForStatus = async (status: ScheduleStatus, startDate: string, endDate: string) => {
  const params = new URLSearchParams()
  params.set('status', status)
  if (startDate) params.set('from', startDate)
  if (endDate) params.set('to', endDate)
  return requestJson<BackendSchedulePeriodResponse[]>(`/api/admin/schedule-periods?${params.toString()}`)
}

const enrichPeriodCounts = async (schedule: WorkSchedule): Promise<WorkSchedule> => {
  const detail = adaptBackendPeriodSchedule(await requestJson<BackendPeriodScheduleResponse>(`/api/admin/schedule-periods/${encodeURIComponent(schedule.id)}/schedule`))
  return {
    ...schedule,
    employeeCount: detail.employeeCount,
    assignmentCount: detail.assignmentCount,
    shiftCount: detail.shiftCount,
  }
}

export const backendScheduleService = {
  async listPeriods({ search = '', status = 'DRAFT', startDate = '', endDate = '' }: ListPeriodParams = {}): Promise<WorkSchedule[]> {
    if (status !== 'ALL' && !backendListableStatuses.includes(status)) return []

    const response = status === 'ALL'
      ? (await Promise.all(backendListableStatuses.map((item) => listPeriodsForStatus(item, startDate, endDate)))).flat()
      : await listPeriodsForStatus(status, startDate, endDate)
    const filtered = filterSearch(response.map(adaptBackendSchedulePeriod), search)
    return Promise.all(filtered.map(enrichPeriodCounts))
  },

  async getPeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}`))
  },

  async createPeriod(payload: CreatePeriodPayload) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>('/api/admin/schedule-periods', {
      method: 'POST',
      body: payload,
    }))
  },

  async approvePeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/approve`, { method: 'PATCH' }))
  },

  async publishPeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/publish`, { method: 'PATCH' }))
  },

  async cancelPeriod(id: string) {
    return adaptBackendSchedulePeriod(await requestJson<BackendSchedulePeriodResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/cancel`, { method: 'PATCH' }))
  },

  async getScheduleDetail(id: string): Promise<ScheduleDetail> {
    return adaptBackendPeriodSchedule(await requestJson<BackendPeriodScheduleResponse>(`/api/admin/schedule-periods/${encodeURIComponent(id)}/schedule`))
  },

  async listWorkShifts(periodId: string) {
    return requestJson<BackendWorkShiftResponse[]>(`/api/admin/schedule-periods/${encodeURIComponent(periodId)}/work-shifts`)
  },

  async createAssignment(workShiftId: string, employeeId: string) {
    return adaptBackendShiftAssignment(await requestJson<BackendShiftAssignmentResponse>(`/api/admin/work-shifts/${encodeURIComponent(workShiftId)}/assignments`, {
      method: 'POST',
      body: { employeeId: Number(employeeId) },
    }))
  },

  async updateAssignment(assignmentId: string, employeeId: string) {
    return adaptBackendShiftAssignment(await requestJson<BackendShiftAssignmentResponse>(`/api/admin/assignments/${encodeURIComponent(assignmentId)}`, {
      method: 'PUT',
      body: { employeeId: Number(employeeId) },
    }))
  },

  async deleteAssignment(assignmentId: string) {
    await requestVoid(`/api/admin/assignments/${encodeURIComponent(assignmentId)}`, { method: 'DELETE' })
  },

  async setAssignmentLocked(assignmentId: string, locked: boolean) {
    const action = locked ? 'lock' : 'unlock'
    return adaptBackendShiftAssignment(await requestJson<BackendShiftAssignmentResponse>(`/api/admin/assignments/${encodeURIComponent(assignmentId)}/${action}`, { method: 'PATCH' }))
  },

  async optimize(periodId: string) {
    return requestJson<BackendOptimizeScheduleResponse>(`/api/admin/schedule-periods/${encodeURIComponent(periodId)}/optimize`, {
      method: 'POST',
      body: {},
    })
  },

  async generateSchedule(payload: BackendGenerateScheduleRequest) {
    return requestJson<BackendGenerateScheduleResponse>('/api/admin/schedule-periods/generate', {
      method: 'POST',
      body: payload,
    })
  },
}

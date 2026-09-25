import { requestJson } from '@/services/apiClient'

export interface BackendEmployeeScheduleItem {
  assignmentId: number
  assignmentStatus: string | null
  assignedSource: string | null
  locked: boolean | null
  workDate: string
  shiftTypeId: number | null
  shiftTypeName: string
  shiftStartTime: string
  shiftEndTime: string
  startsAt: string | null
  endsAt: string | null
  workMinutes: number | null
  isTransitionShift: boolean | null
  periodId: number | null
  periodName: string | null
  periodStatus: string | null
}

export const employeeScheduleService = {
  async list({ from, to }: { from: string; to: string }): Promise<BackendEmployeeScheduleItem[]> {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return requestJson<BackendEmployeeScheduleItem[]>(`/api/employee/schedule${query ? `?${query}` : ''}`)
  },
}

import { adaptBackendSchedulePeriod } from '@/adapters/scheduleAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendSchedulePeriodResponse } from '@/types/backendSchedule'
import type { WorkSchedule } from '@/types/schedule'

export const backendEmployeePoolService = {
  async update(periodId: string, employeeIds: number[]): Promise<WorkSchedule> {
    const response = await requestJson<BackendSchedulePeriodResponse>(
      `/api/admin/schedule-periods/${encodeURIComponent(periodId)}/employee-pool`,
      { method: 'PUT', body: { employeeIds } },
    )
    return adaptBackendSchedulePeriod(response)
  },
}
import { apiConfig } from '@/config/api'
import { requestJson } from '@/services/apiClient'

export interface EmployeeShiftTypeOption {
  id: number
  shiftCode: string
  shiftName: string
  startTime: string
  endTime: string
  quotaCategory: string | null
}

export const backendEmployeeShiftTypeService = {
  async listActive(): Promise<EmployeeShiftTypeOption[]> {
    if (apiConfig.useMockApi) {
      return []
    }
    return requestJson<EmployeeShiftTypeOption[]>('/api/employee/shift-types')
  },
}

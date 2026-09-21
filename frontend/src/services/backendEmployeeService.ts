import { adaptBackendAccount, adaptBackendEmployee } from '@/adapters/employeeAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { UserAccount } from '@/types/account'
import type { BackendAdminEmployeeResponse } from '@/types/backendEmployee'
import type { Employee, EmployeeFormValues, PaginatedResult } from '@/types/management'

const pageResult = <T>(items: T[], page: number, pageSize: number): PaginatedResult<T> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const employeePayload = (data: EmployeeFormValues) => ({
  employeeCode: data.employeeCode.trim(),
  fullName: data.fullName.trim(),
  email: data.email.trim(),
  phone: data.phone.trim(),
  status: data.status,
})

export const backendEmployeeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string }) {
    const params = new URLSearchParams()
    if (search.trim()) params.set('query', search.trim())
    if (status !== 'ALL') params.set('status', status)
    const query = params.toString()
    const response = await requestJson<BackendAdminEmployeeResponse[]>(`/api/admin/employees${query ? `?${query}` : ''}`)
    return pageResult(response.map(adaptBackendEmployee), page, pageSize)
  },

  async listAccounts(): Promise<UserAccount[]> {
    const response = await requestJson<BackendAdminEmployeeResponse[]>('/api/admin/employees')
    return response.map(adaptBackendAccount).filter((account): account is UserAccount => account !== null)
  },

  async getById(id: string): Promise<Employee | null> {
    const employee = await requestJson<BackendAdminEmployeeResponse>(`/api/admin/employees/${id}`)
    return adaptBackendEmployee(employee)
  },

  async add(data: EmployeeFormValues): Promise<Employee> {
    const employee = await requestJson<BackendAdminEmployeeResponse>('/api/admin/employees', {
      method: 'POST',
      body: employeePayload(data),
    })
    return adaptBackendEmployee(employee)
  },

  async update(id: string, data: EmployeeFormValues): Promise<Employee> {
    const employee = await requestJson<BackendAdminEmployeeResponse>(`/api/admin/employees/${id}`, {
      method: 'PUT',
      body: {
        fullName: data.fullName.trim(),
        email: data.email.trim(),
        phone: data.phone.trim(),
        status: data.status,
      },
    })
    return adaptBackendEmployee(employee)
  },

  async lock(id: string): Promise<void> {
    await requestVoid(`/api/admin/employees/${id}/lock`, { method: 'POST' })
  },

  async unlock(id: string): Promise<void> {
    await requestVoid(`/api/admin/employees/${id}/unlock`, { method: 'POST' })
  },
}

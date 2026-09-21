import type { BackendAdminEmployeeResponse } from '@/types/backendEmployee'
import type { UserAccount, AccountStatus } from '@/types/account'
import type { Employee } from '@/types/management'

export const adaptBackendEmployee = (employee: BackendAdminEmployeeResponse): Employee => ({
  id: String(employee.id),
  employeeCode: employee.employeeCode,
  fullName: employee.fullName,
  email: employee.email ?? '',
  phone: employee.phone ?? '',
  role: employee.account?.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
  status: employee.status,
  joinDate: new Date().toISOString().slice(0, 10),
  note: undefined,
})

export const adaptBackendAccount = (employee: BackendAdminEmployeeResponse): UserAccount | null => {
  if (!employee.account) {
    return null
  }

  return {
    id: String(employee.account.id),
    employeeId: String(employee.id),
    username: employee.account.username,
    email: employee.email ?? '',
    role: employee.account.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
    status: employee.account.status as AccountStatus,
    createdAt: new Date().toISOString(),
    activatedAt: null,
    lastLoginAt: null,
  }
}

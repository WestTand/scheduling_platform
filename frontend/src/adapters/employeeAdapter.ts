import type { BackendAdminEmployeeResponse } from '@/types/backendEmployee'
import type { UserAccount } from '@/types/account'
import type { Employee } from '@/types/management'

export const adaptBackendEmployee = (employee: BackendAdminEmployeeResponse): Employee => ({
  id: String(employee.id),
  employeeCode: employee.employeeCode,
  fullName: employee.fullName,
  email: employee.email ?? '',
  phone: employee.phone ?? '',
  // ADMIN collapses to EMPLOYEE because frontend Employee/UserAccount only allow MANAGER|EMPLOYEE.
  role: employee.account?.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
  status: employee.status,
  // Backend AdminEmployeeResponse currently has no date fields, so joinDate defaults to today.
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
    // ADMIN collapses to EMPLOYEE because frontend Employee/UserAccount only allow MANAGER|EMPLOYEE.
    role: employee.account.role === 'MANAGER' ? 'MANAGER' : 'EMPLOYEE',
    status: employee.account.status,
    // Backend AdminEmployeeResponse currently has no date fields, so createdAt defaults to today.
    createdAt: new Date().toISOString(),
    activatedAt: null,
    lastLoginAt: null,
  }
}

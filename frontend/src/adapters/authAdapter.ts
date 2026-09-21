import { Role, type User } from '@/types/auth'
import type { BackendAccountResponse, BackendMeResponse } from '@/types/backendAuth'

const toFrontendRole = (role: BackendAccountResponse['role']): Role => {
  switch (role) {
    case 'ADMIN':
    case 'MANAGER':
      return Role.MANAGER
    case 'EMPLOYEE':
      return Role.EMPLOYEE
    default:
      throw new Error(`Unsupported backend account role: ${String(role)}`)
  }
}

export const adaptBackendMeToUser = (response: BackendMeResponse): User => {
  const employee = response.employee
  const displayName = employee?.fullName || response.account.username

  return {
    id: String(employee?.id ?? response.account.id),
    name: displayName,
    email: employee?.email || response.account.username,
    role: toFrontendRole(response.account.role),
    department: employee?.employeeCode || 'Scheduling',
    avatar: displayName.slice(0, 2).toUpperCase(),
  }
}

export const adaptBackendAccountToUser = (account: BackendAccountResponse): User => {
  return {
    id: String(account.id),
    name: account.username,
    email: account.username,
    role: toFrontendRole(account.role),
    department: 'Scheduling',
    avatar: account.username.slice(0, 2).toUpperCase(),
  }
}

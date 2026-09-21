import type { AccountStatus } from '@/types/account'

export interface BackendAdminEmployeeAccountResponse {
  id: number
  username: string
  role: 'MANAGER' | 'EMPLOYEE' | 'ADMIN'
  status: AccountStatus | 'FORCE_PASSWORD_CHANGE' | 'INACTIVE'
  active: boolean
}

export interface BackendAdminEmployeeResponse {
  id: number
  employeeCode: string
  fullName: string
  email?: string | null
  phone?: string | null
  status: 'ACTIVE' | 'INACTIVE'
  account?: BackendAdminEmployeeAccountResponse | null
}

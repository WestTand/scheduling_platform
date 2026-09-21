export const AccountStatus = {
  PENDING_ACTIVATION: 'PENDING_ACTIVATION',
  FORCE_PASSWORD_CHANGE: 'FORCE_PASSWORD_CHANGE',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  LOCKED: 'LOCKED',
  DISABLED: 'DISABLED',
} as const

export type AccountStatus = (typeof AccountStatus)[keyof typeof AccountStatus]

export interface UserAccount {
  id: string
  employeeId: string
  username: string
  email: string
  role: 'MANAGER' | 'EMPLOYEE'
  status: AccountStatus
  createdAt: string
  activatedAt?: string | null
  lastLoginAt?: string | null
}

export interface CreateEmployeeAccountInput {
  employeeId: string
  username: string
  email: string
  temporaryPassword: string
}

export interface ActivationRequest {
  username: string
  temporaryPassword: string
}

export interface OtpVerificationInput {
  accountId: string
  otp: string
}

export interface SetPasswordInput {
  accountId: string
  newPassword: string
  confirmPassword: string
}

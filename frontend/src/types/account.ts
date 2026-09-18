export const AccountStatus = {
  PENDING_ACTIVATION: 'PENDING_ACTIVATION',
  ACTIVE: 'ACTIVE',
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

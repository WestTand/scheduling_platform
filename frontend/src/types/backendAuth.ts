export type BackendAccountRole = 'ADMIN' | 'MANAGER' | 'EMPLOYEE'

export interface BackendAccountResponse {
  id: number
  username: string
  role: BackendAccountRole
}

export interface BackendEmployeeResponse {
  id: number
  employeeCode: string
  fullName: string
  email: string | null
  phone: string | null
  status: string
}

export interface BackendAuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  account: BackendAccountResponse
}

export interface BackendMeResponse {
  account: BackendAccountResponse
  employee: BackendEmployeeResponse | null
}

export interface BackendLoginStateResponse {
  status: 'OTP_REQUIRED' | 'PASSWORD_CHANGE_REQUIRED'
  accountId: number
  username: string
}

export const isBackendLoginStateResponse = (value: unknown): value is BackendLoginStateResponse => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'status' in value &&
      'accountId' in value &&
      'username' in value &&
      ((value as { status?: unknown }).status === 'OTP_REQUIRED' ||
        (value as { status?: unknown }).status === 'PASSWORD_CHANGE_REQUIRED') &&
      typeof (value as { accountId?: unknown }).accountId === 'number' &&
      typeof (value as { username?: unknown }).username === 'string',
  )
}

export interface BackendActivationStateResponse {
  status: 'PASSWORD_CHANGE_REQUIRED'
  accountId: number
  username: string
}

export const isBackendActivationStateResponse = (value: unknown): value is BackendActivationStateResponse => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'status' in value &&
      'accountId' in value &&
      'username' in value &&
      (value as { status?: unknown }).status === 'PASSWORD_CHANGE_REQUIRED' &&
      typeof (value as { accountId?: unknown }).accountId === 'number' &&
      typeof (value as { username?: unknown }).username === 'string',
  )
}

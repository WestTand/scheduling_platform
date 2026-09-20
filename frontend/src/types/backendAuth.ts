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
  email: string
  phone: string
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
      ((value as { status?: unknown }).status === 'OTP_REQUIRED' ||
        (value as { status?: unknown }).status === 'PASSWORD_CHANGE_REQUIRED'),
  )
}

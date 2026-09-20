import { adaptBackendAccountToUser, adaptBackendMeToUser } from '@/adapters/authAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { LoginCredentials, User } from '@/types/auth'
import {
  isBackendLoginStateResponse,
  type BackendAuthResponse,
  type BackendLoginStateResponse,
  type BackendMeResponse,
} from '@/types/backendAuth'

export interface BackendLoginResult {
  accessToken: string
  user: User
}

export interface BackendBootstrapResult {
  accessToken?: string
  user: User
}

const loginStateError = (response: BackendLoginStateResponse): never => {
  if (response.status === 'OTP_REQUIRED') {
    throw new Error('Tài khoản chưa được kích hoạt.')
  }

  throw new Error('Tài khoản cần đổi mật khẩu trước khi tiếp tục.')
}

export const backendAuthService = {
  async login(credentials: LoginCredentials): Promise<BackendLoginResult> {
    const response = await requestJson<BackendAuthResponse | BackendLoginStateResponse>('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: {
        username: credentials.email.trim(),
        password: credentials.password,
      },
    })

    if (isBackendLoginStateResponse(response)) {
      return loginStateError(response)
    }

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },

  async me(): Promise<User> {
    const response = await requestJson<BackendMeResponse>('/api/auth/me')

    return adaptBackendMeToUser(response)
  },

  async refresh(): Promise<BackendBootstrapResult> {
    const response = await requestJson<BackendAuthResponse>('/api/auth/refresh', {
      method: 'POST',
      auth: false,
    })

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },

  async logout(): Promise<void> {
    await requestVoid('/api/auth/logout', {
      method: 'POST',
      auth: false,
    })
  },
}

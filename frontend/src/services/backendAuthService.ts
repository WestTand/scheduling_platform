import { adaptBackendAccountToUser, adaptBackendMeToUser } from '@/adapters/authAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { LoginCredentials, User } from '@/types/auth'
import {
  isBackendActivationStateResponse,
  isBackendLoginStateResponse,
  type BackendActivationStateResponse,
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

const loginStateMessage = (state: BackendLoginStateResponse) => {
  return state.status === 'OTP_REQUIRED'
    ? 'Tài khoản chưa được kích hoạt.'
    : 'Tài khoản cần đổi mật khẩu trước khi tiếp tục.'
}

export class BackendLoginStateError extends Error {
  state: BackendLoginStateResponse

  constructor(state: BackendLoginStateResponse) {
    super(loginStateMessage(state))
    this.name = 'BackendLoginStateError'
    this.state = state
  }
}

const requireActivationState = (response: BackendActivationStateResponse) => {
  if (!isBackendActivationStateResponse(response)) {
    throw new Error('Trạng thái kích hoạt tài khoản không hợp lệ.')
  }
  return response
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
      throw new BackendLoginStateError(response)
    }

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },

  async verifyActivationOtp(accountId: number, otp: string): Promise<BackendActivationStateResponse> {
    const response = await requestJson<BackendActivationStateResponse>('/api/auth/activation/verify-otp', {
      method: 'POST',
      auth: false,
      body: { accountId, otp },
    })
    return requireActivationState(response)
  },

  async resendActivationOtp(username: string, temporaryPassword: string): Promise<void> {
    await requestVoid('/api/auth/activation/resend', { method: 'POST', auth: false, body: { username: username.trim(), temporaryPassword } })
  },

  async changeRequiredPassword(accountId: number, currentPassword: string, newPassword: string): Promise<BackendLoginResult> {
    const response = await requestJson<BackendAuthResponse>('/api/auth/password/change-required', {
      method: 'POST',
      auth: false,
      body: { accountId, currentPassword, newPassword },
    })
    return { accessToken: response.accessToken, user: adaptBackendAccountToUser(response.account) }
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

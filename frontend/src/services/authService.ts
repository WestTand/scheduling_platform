import { apiConfig } from '@/config/api'
import { mockUsers } from '@/mocks/users'
import { accountService } from '@/services/accountService'
import { backendAuthService } from '@/services/backendAuthService'
import { AUTH_STORAGE_KEYS, getStoredToken } from '@/services/authStorage'
import type { LoginCredentials, User } from '@/types/auth'

const AUTH_DELAY_MS = 700

export { AUTH_STORAGE_KEYS, getStoredToken }

const storageForRememberMe = (rememberMe: boolean) => (rememberMe ? localStorage : sessionStorage)

const getStoredUserStorage = () => {
  if (sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) || sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)) {
    return sessionStorage
  }

  if (localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) || localStorage.getItem(AUTH_STORAGE_KEYS.USER)) {
    return localStorage
  }

  return sessionStorage
}

const getStoredTokenStorage = () => {
  if (sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)) {
    return sessionStorage
  }

  if (localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)) {
    return localStorage
  }

  return sessionStorage
}

const mockLoginInternal = async (credentials: LoginCredentials): Promise<User> => {
  await new Promise((resolve) => {
    window.setTimeout(resolve, AUTH_DELAY_MS)
  })

  const input = credentials.email.trim()
  const legacyAccount = mockUsers.find(
    (user) => user.email.toLowerCase() === input.toLowerCase() || user.name.toLowerCase() === input.toLowerCase(),
  )

  const linkedAccount = await accountService.getAccountByIdentifier(input)
  const passwordMatch = linkedAccount ? await accountService.getPasswordForAccount(linkedAccount.id) === credentials.password : false

  if (legacyAccount && legacyAccount.password === credentials.password) {
    const safeUser = {
      id: legacyAccount.id,
      name: legacyAccount.name,
      email: legacyAccount.email,
      role: legacyAccount.role,
      department: legacyAccount.department,
      avatar: legacyAccount.avatar,
    }

    return safeUser
  }

  if (!linkedAccount || !passwordMatch) {
    throw new Error('Thông tin đăng nhập không đúng. Vui lòng kiểm tra lại email hoặc tên tài khoản và mật khẩu.')
  }

  if (linkedAccount.status === 'PENDING_ACTIVATION') {
    throw new Error('Tài khoản chưa được kích hoạt.')
  }

  if (linkedAccount.status === 'LOCKED' || linkedAccount.status === 'DISABLED') {
    throw new Error('Tài khoản này đã bị khóa hoặc vô hiệu hóa.')
  }

  if (linkedAccount.status !== 'ACTIVE') {
    throw new Error('Tài khoản chưa được kích hoạt.')
  }

  const employeeUser = await accountService.getUserForAccount(linkedAccount.id)

  if (!employeeUser) {
    throw new Error('Không tìm thấy thông tin nhân viên cho tài khoản này.')
  }

  return employeeUser
}

export const setStoredAuth = (user: User, rememberMe: boolean, accessToken?: string) => {
  const token = accessToken ?? (!apiConfig.useMockApi ? getStoredToken() : null) ?? `mock-token-${user.role.toLowerCase()}-${user.id}`
  const storage = storageForRememberMe(rememberMe)

  clearStoredAuth()

  storage.setItem(AUTH_STORAGE_KEYS.TOKEN, token)
  storage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
}

export const updateStoredAccessToken = (accessToken: string) => {
  getStoredTokenStorage().setItem(AUTH_STORAGE_KEYS.TOKEN, accessToken)
}

export const clearStoredAuth = () => {
  localStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  localStorage.removeItem(AUTH_STORAGE_KEYS.USER)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.USER)
}

export const restoreStoredUser = () => {
  const localUser = localStorage.getItem(AUTH_STORAGE_KEYS.USER)

  if (localUser) {
    try {
      return JSON.parse(localUser) as User
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEYS.USER)
    }
  }

  const sessionUser = sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)

  if (sessionUser) {
    try {
      return JSON.parse(sessionUser) as User
    } catch {
      sessionStorage.removeItem(AUTH_STORAGE_KEYS.USER)
    }
  }

  return null
}

const storeUserInExistingStorage = (user: User) => {
  getStoredUserStorage().setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
}

export const authService = {
  async login(credentials: LoginCredentials, rememberMe: boolean): Promise<User> {
    try {
      if (apiConfig.useMockApi) {
        const user = await mockLoginInternal(credentials)
        setStoredAuth(user, rememberMe)
        return user
      }

      const result = await backendAuthService.login(credentials)
      setStoredAuth(result.user, rememberMe, result.accessToken)
      return result.user
    } catch (error) {
      clearStoredAuth()
      throw error
    }
  },

  async getCurrentUser(): Promise<User | null> {
    if (apiConfig.useMockApi) {
      return restoreStoredUser()
    }

    if (!getStoredToken()) {
      return null
    }

    try {
      const user = await backendAuthService.me()
      storeUserInExistingStorage(user)
      return user
    } catch {
      clearStoredAuth()
      return null
    }
  },

  async refresh(): Promise<User | null> {
    if (apiConfig.useMockApi) {
      return restoreStoredUser()
    }

    try {
      const result = await backendAuthService.refresh()
      if (result.accessToken) {
        updateStoredAccessToken(result.accessToken)
      }
      storeUserInExistingStorage(result.user)
      return result.user
    } catch {
      clearStoredAuth()
      return null
    }
  },

  async bootstrapAuth(): Promise<User | null> {
    const storedUser = restoreStoredUser()

    if (apiConfig.useMockApi) {
      return storedUser
    }

    if (!getStoredToken()) {
      return null
    }

    try {
      const user = await backendAuthService.me()
      storeUserInExistingStorage(user)
      return user
    } catch {
      try {
        const refreshed = await backendAuthService.refresh()

        if (refreshed.accessToken) {
          updateStoredAccessToken(refreshed.accessToken)
        }

        try {
          const user = await backendAuthService.me()
          storeUserInExistingStorage(user)
          return user
        } catch {
          storeUserInExistingStorage(refreshed.user)
          return refreshed.user
        }
      } catch {
        clearStoredAuth()
        return null
      }
    }
  },

  async logout(): Promise<void> {
    if (!apiConfig.useMockApi) {
      try {
        await backendAuthService.logout()
      } catch {
        // Always clear local auth state even if the backend session is already gone.
      }
    }

    clearStoredAuth()
  },
}

export const mockLogin = (credentials: LoginCredentials): Promise<User> => authService.login(credentials, false)
export const getCurrentUser = (): Promise<User | null> => authService.getCurrentUser()
export const refreshAuth = (): Promise<User | null> => authService.refresh()
export const bootstrapAuth = (): Promise<User | null> => authService.bootstrapAuth()
export const logout = (): Promise<void> => authService.logout()

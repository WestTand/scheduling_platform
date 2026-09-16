import { mockUsers } from '@/mocks/users'
import type { LoginCredentials, User } from '@/types/auth'

const AUTH_DELAY_MS = 700

export const AUTH_STORAGE_KEYS = {
  TOKEN: 'schedule_auth_token',
  USER: 'schedule_auth_user',
}

export const mockLogin = async (credentials: LoginCredentials): Promise<User> => {
  await new Promise((resolve) => {
    window.setTimeout(resolve, AUTH_DELAY_MS)
  })

  const account = mockUsers.find((user) => user.email.toLowerCase() === credentials.email.trim().toLowerCase())

  if (!account || account.password !== credentials.password) {
    throw new Error('Thông tin đăng nhập không đúng. Vui lòng kiểm tra lại email và mật khẩu.')
  }

  const safeUser = {
    id: account.id,
    name: account.name,
    email: account.email,
    role: account.role,
    department: account.department,
    avatar: account.avatar,
  }

  return safeUser
}

export const getStoredToken = () => {
  return localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ?? sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)
}

export const setStoredAuth = (user: User, rememberMe: boolean) => {
  const token = `mock-token-${user.role.toLowerCase()}-${user.id}`
  const storage = rememberMe ? localStorage : sessionStorage

  storage.setItem(AUTH_STORAGE_KEYS.TOKEN, token)
  storage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
}

export const clearStoredAuth = () => {
  localStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  localStorage.removeItem(AUTH_STORAGE_KEYS.USER)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.USER)
}

export const restoreStoredUser = () => {
  const storedUser = localStorage.getItem(AUTH_STORAGE_KEYS.USER) ?? sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser) as User
  } catch {
    return null
  }
}

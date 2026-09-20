import { mockUsers } from '@/mocks/users'
import { accountService } from '@/services/accountService'
import { AUTH_STORAGE_KEYS, getStoredToken } from '@/services/authStorage'
import type { LoginCredentials, User } from '@/types/auth'

const AUTH_DELAY_MS = 700

export { AUTH_STORAGE_KEYS, getStoredToken }

export const mockLogin = async (credentials: LoginCredentials): Promise<User> => {
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

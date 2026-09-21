import { apiConfig } from '@/config/api'
import { employeeService } from '@/services/employeeService'
import { backendManagerAccountService } from '@/services/backendManagerAccountService'
import type { UserAccount, AccountStatus, CreateEmployeeAccountInput } from '@/types/account'
import type { User } from '@/types/auth'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

export const MOCK_OTP = '123456'

interface AccountSecretRecord {
  accountId: string
  password: string
}

const normalize = (value: string) => value.trim().toLowerCase()

const maskEmail = (email: string) => {
  const [localPart, domainPart = ''] = email.split('@')
  if (!domainPart) {
    return email
  }

  const safeLocal = localPart.length > 2 ? `${localPart.slice(0, 2)}***` : `${localPart.slice(0, 1)}***`
  return `${safeLocal}@${domainPart}`
}

const hasPasswordRequirements = (password: string) => {
  const hasMinLength = password.length >= 8
  const hasUppercase = /[A-Z]/.test(password)
  const hasLowercase = /[a-z]/.test(password)
  const hasDigit = /\d/.test(password)

  return hasMinLength && hasUppercase && hasLowercase && hasDigit
}

const buildUserFromAccount = async (account: UserAccount): Promise<User> => {
  const employee = await employeeService.getById(account.employeeId)

  return {
    id: account.employeeId,
    name: employee?.fullName ?? account.username,
    email: account.email,
    role: account.role,
    department: employee?.note ?? 'Phòng vận hành',
    avatar: account.username.slice(0, 2).toUpperCase(),
  }
}

const seedAccounts: UserAccount[] = [
  {
    id: 'acct-manager-01',
    employeeId: 'emp-001',
    username: 'manager',
    email: 'manager@schedule.local',
    role: 'MANAGER',
    status: 'ACTIVE' as AccountStatus,
    createdAt: '2024-01-01T00:00:00.000Z',
    activatedAt: '2024-01-01T00:00:00.000Z',
    lastLoginAt: null,
  },
  {
    id: 'acct-employee-01',
    employeeId: 'emp-002',
    username: 'employee',
    email: 'employee@schedule.local',
    role: 'EMPLOYEE',
    status: 'ACTIVE' as AccountStatus,
    createdAt: '2024-01-01T00:00:00.000Z',
    activatedAt: '2024-01-01T00:00:00.000Z',
    lastLoginAt: null,
  },
]

let mockAccounts: UserAccount[] = clone(seedAccounts)
let accountSecrets: Record<string, AccountSecretRecord> = {
  'acct-manager-01': { accountId: 'acct-manager-01', password: '123456' },
  'acct-employee-01': { accountId: 'acct-employee-01', password: '123456' },
}
let otpStore: Record<string, string> = {}
let otpExpiryStore: Record<string, number> = {}

export const accountService = {
  async getAccounts() {
    if (!apiConfig.useMockApi) {
      return backendManagerAccountService.getAccounts()
    }
    await delay(150)
    return clone(mockAccounts)
  },

  async getAccountByEmployeeId(employeeId: string) {
    if (!apiConfig.useMockApi) {
      return backendManagerAccountService.getAccountByEmployeeId(employeeId)
    }
    await delay(100)
    return mockAccounts.find((account) => account.employeeId === employeeId) ?? null
  },

  async getAccountById(accountId: string) {
    if (!apiConfig.useMockApi) {
      return backendManagerAccountService.getAccountById(accountId)
    }
    await delay(100)
    return mockAccounts.find((account) => account.id === accountId) ?? null
  },

  async getAccountByIdentifier(identifier: string) {
    await delay(100)
    const normalized = normalize(identifier)
    return mockAccounts.find((account) => normalize(account.username) === normalized || normalize(account.email) === normalized) ?? null
  },

  async getPasswordForAccount(accountId: string) {
    await delay(50)
    return accountSecrets[accountId]?.password ?? null
  },

  async createEmployeeAccount(input: CreateEmployeeAccountInput) {
    if (!apiConfig.useMockApi) {
      return backendManagerAccountService.createEmployeeAccount(input)
    }
    await delay(400)

    const employee = await employeeService.getById(input.employeeId)
    if (!employee) {
      throw new Error('Nhân viên không tồn tại trong hệ thống.')
    }

    if (employee.role !== 'EMPLOYEE') {
      throw new Error('Chỉ nhân viên mới có thể được tạo tài khoản.')
    }

    if (!employee.email.trim()) {
      throw new Error('Nhân viên chưa có email. Hãy cập nhật email trước khi tạo tài khoản.')
    }

    const existingAccount = mockAccounts.find((account) => account.employeeId === input.employeeId)
    if (existingAccount) {
      throw new Error('Nhân viên này đã có tài khoản.')
    }

    const duplicateUsername = mockAccounts.some((account) => normalize(account.username) === normalize(input.username))
    if (duplicateUsername) {
      throw new Error('Tên tài khoản đã tồn tại. Vui lòng chọn tên tài khoản khác.')
    }

    const duplicateEmail = mockAccounts.some((account) => normalize(account.email) === normalize(employee.email))
    if (duplicateEmail) {
      throw new Error('Email đã được liên kết với tài khoản khác.')
    }

    const mockTemporaryPassword = 'Temp1234'

    const account: UserAccount = {
      id: `acct-${Date.now()}`,
      employeeId: input.employeeId,
      username: input.username.trim(),
      email: employee.email.trim(),
      role: 'EMPLOYEE',
      status: 'PENDING_ACTIVATION' as AccountStatus,
      createdAt: new Date().toISOString(),
      activatedAt: null,
      lastLoginAt: null,
    }

    mockAccounts = [account, ...mockAccounts]
    accountSecrets[account.id] = { accountId: account.id, password: mockTemporaryPassword }
    otpStore[account.id] = MOCK_OTP
    otpExpiryStore[account.id] = Date.now() + 5 * 60 * 1000

    return clone(account)
  },

  async requestActivation(username: string, temporaryPassword: string) {
    await delay(350)

    const account = mockAccounts.find(
      (item) => normalize(item.username) === normalize(username) && item.status === 'PENDING_ACTIVATION',
    )

    if (!account) {
      throw new Error('Thông tin tài khoản không hợp lệ hoặc tài khoản không thể kích hoạt.')
    }

    const storedPassword = accountSecrets[account.id]?.password
    if (!storedPassword || storedPassword !== temporaryPassword) {
      throw new Error('Thông tin tài khoản không hợp lệ hoặc tài khoản không thể kích hoạt.')
    }

    otpStore[account.id] = MOCK_OTP
    otpExpiryStore[account.id] = Date.now() + 5 * 60 * 1000

    return {
      account: clone(account),
      maskedEmail: maskEmail(account.email),
    }
  },

  async verifyActivationOtp(accountId: string, otp: string) {
    await delay(250)

    const account = mockAccounts.find((item) => item.id === accountId)
    if (!account) {
      throw new Error('Tài khoản không tồn tại.')
    }

    if (account.status !== 'PENDING_ACTIVATION') {
      throw new Error('Tài khoản này không cần kích hoạt nữa.')
    }

    const expiryAt = otpExpiryStore[accountId]
    if (!expiryAt || Date.now() > expiryAt) {
      throw new Error('Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.')
    }

    if (!otp || otp.trim() !== MOCK_OTP) {
      throw new Error('Mã OTP không đúng. Vui lòng kiểm tra lại.')
    }

    return clone(account)
  },

  async setNewPassword(accountId: string, newPassword: string) {
    await delay(350)

    const account = mockAccounts.find((item) => item.id === accountId)
    if (!account) {
      throw new Error('Tài khoản không tồn tại.')
    }

    const temporaryPassword = accountSecrets[accountId]?.password
    if (temporaryPassword && newPassword === temporaryPassword) {
      throw new Error('Mật khẩu mới không được trùng với mật khẩu tạm thời.')
    }

    if (!hasPasswordRequirements(newPassword)) {
      throw new Error('Mật khẩu mới phải tối thiểu 8 ký tự, có chữ hoa, chữ thường và chữ số.')
    }

    accountSecrets[account.id] = { accountId: account.id, password: newPassword }
    mockAccounts = mockAccounts.map((item) =>
      item.id === accountId
        ? {
            ...item,
            status: 'ACTIVE' as AccountStatus,
            activatedAt: new Date().toISOString(),
            lastLoginAt: new Date().toISOString(),
          }
        : item,
    )

    otpStore[account.id] = ''
    otpExpiryStore[account.id] = 0

    return clone(mockAccounts.find((item) => item.id === accountId) ?? account)
  },

  async resendActivationOtp(accountId: string) {
    if (!apiConfig.useMockApi) {
      await backendManagerAccountService.resendActivationOtp(accountId)
      const account = await backendManagerAccountService.getAccountById(accountId)
      return { account, maskedEmail: account?.email ?? '' }
    }
    await delay(300)

    const account = mockAccounts.find((item) => item.id === accountId)
    if (!account) {
      throw new Error('Tài khoản không tồn tại.')
    }

    if (account.status !== 'PENDING_ACTIVATION') {
      throw new Error('Chỉ tài khoản đang chờ kích hoạt mới có thể gửi lại OTP.')
    }

    otpStore[account.id] = MOCK_OTP
    otpExpiryStore[account.id] = Date.now() + 5 * 60 * 1000

    return {
      account: clone(account),
      maskedEmail: maskEmail(account.email),
    }
  },

  async getUserForAccount(accountId: string) {
    const account = await this.getAccountById(accountId)
    if (!account) {
      return null
    }

    return buildUserFromAccount(account)
  },

  async findAccountByLoginIdentifier(identifier: string) {
    return this.getAccountByIdentifier(identifier)
  },

  async resetMockData() {
    mockAccounts = clone(seedAccounts)
    accountSecrets = {
      'acct-manager-01': { accountId: 'acct-manager-01', password: '123456' },
      'acct-employee-01': { accountId: 'acct-employee-01', password: '123456' },
    }
    otpStore = {}
    otpExpiryStore = {}
  },
}

export const buildAccountPayload = () => ({
  getMaskedEmail: maskEmail,
  devOtp: MOCK_OTP,
})

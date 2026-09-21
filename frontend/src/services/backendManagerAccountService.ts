import { backendEmployeeService } from '@/services/backendEmployeeService'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { CreateEmployeeAccountInput, UserAccount } from '@/types/account'
import type { BackendAuthResponse } from '@/types/backendAuth'

type BackendAccount = BackendAuthResponse['account']

export const backendManagerAccountService = {
  async getAccounts(): Promise<UserAccount[]> {
    return backendEmployeeService.listAccounts()
  },

  async getAccountByEmployeeId(employeeId: string): Promise<UserAccount | null> {
    const accounts = await backendEmployeeService.listAccounts()
    return accounts.find((account) => account.employeeId === employeeId) ?? null
  },

  async getAccountById(accountId: string): Promise<UserAccount | null> {
    const accounts = await backendEmployeeService.listAccounts()
    return accounts.find((account) => account.id === accountId) ?? null
  },

  async createEmployeeAccount(input: CreateEmployeeAccountInput): Promise<UserAccount> {
    const created = await requestJson<BackendAccount>('/api/manager/accounts', {
      method: 'POST',
      body: { employeeId: Number(input.employeeId), username: input.username.trim() },
    })
    const account = await this.getAccountById(String(created.id))
    if (!account) {
      throw new Error('Không tìm thấy tài khoản vừa tạo.')
    }
    return account
  },

  async resendActivationOtp(accountId: string): Promise<void> {
    await requestVoid(`/api/manager/accounts/${accountId}/activation/resend`, { method: 'POST' })
  },

  async resetPassword(accountId: string): Promise<void> {
    await requestVoid(`/api/manager/accounts/${accountId}/password/reset`, { method: 'POST' })
  },
}

import { describe, expect, it } from 'vitest'

import { adaptBackendEmployee } from './employeeAdapter'

describe('adaptBackendEmployee', () => {
  it('marks only active employee accounts as AI schedulable', () => {
    const schedulable = adaptBackendEmployee({
      id: 1,
      employeeCode: 'EMP001',
      fullName: 'Nguyễn Văn An',
      email: 'emp001@example.com',
      phone: '0900000001',
      status: 'ACTIVE',
      account: { id: 11, username: 'emp001@example.com', role: 'EMPLOYEE', status: 'ACTIVE', active: true },
    })
    const missingAccount = adaptBackendEmployee({
      id: 2,
      employeeCode: 'EMP002',
      fullName: 'Trần Thị Bình',
      email: 'emp002@example.com',
      phone: '0900000002',
      status: 'ACTIVE',
      account: null,
    })
    const inactiveAccount = adaptBackendEmployee({
      id: 3,
      employeeCode: 'EMP003',
      fullName: 'Lê Minh Châu',
      email: 'emp003@example.com',
      phone: '0900000003',
      status: 'ACTIVE',
      account: { id: 13, username: 'emp003@example.com', role: 'EMPLOYEE', status: 'INACTIVE', active: true },
    })

    expect(schedulable.aiSchedulable).toBe(true)
    expect(missingAccount.aiSchedulable).toBe(false)
    expect(inactiveAccount.aiSchedulable).toBe(false)
  })
})

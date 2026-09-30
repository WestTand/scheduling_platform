import { describe, expect, test } from 'vitest'

import { getLeaveSubmitErrorDialogMessage } from './EmployeeLeaveRequestsPage.helpers'

describe('EmployeeLeaveRequestsPage helpers', () => {
  test('uses backend duplicate leave message for submit error dialog', () => {
    expect(getLeaveSubmitErrorDialogMessage('Đã có yêu cầu nghỉ chờ duyệt hoặc đã duyệt trong khoảng ngày này.'))
      .toBe('Đã có yêu cầu nghỉ chờ duyệt hoặc đã duyệt trong khoảng ngày này.')
  })

  test('falls back to a generic submit error dialog message for blank errors', () => {
    expect(getLeaveSubmitErrorDialogMessage('   ')).toBe('Không thể tạo yêu cầu nghỉ. Vui lòng thử lại.')
  })
})

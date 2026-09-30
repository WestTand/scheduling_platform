import { describe, expect, test } from 'vitest'

import { getAppDialogConfig } from './AppDialogProvider.helpers'

describe('AppDialogProvider helpers', () => {
  test('maps success dialogs to a positive title and button label', () => {
    expect(getAppDialogConfig({ variant: 'success', description: 'Đã gửi yêu cầu.' })).toEqual({
      title: 'Thành công',
      description: 'Đã gửi yêu cầu.',
      confirmLabel: 'Đóng',
      iconClassName: 'bg-primary/10 text-primary',
    })
  })

  test('keeps a custom title while defaulting blank descriptions', () => {
    expect(getAppDialogConfig({ title: 'Không thể thực hiện', description: '   ', variant: 'error' })).toMatchObject({
      title: 'Không thể thực hiện',
      description: 'Đã xảy ra lỗi. Vui lòng thử lại.',
      confirmLabel: 'Đóng',
    })
  })
})

import { describe, expect, test } from 'vitest'

import { validateExportSelection } from './ExportsPage.helpers'

describe('ExportsPage helpers', () => {
  test('requires a selected period', () => {
    expect(validateExportSelection('')).toBe('Vui lòng chọn kỳ lịch để xuất file.')
  })

  test('accepts a selected period', () => {
    expect(validateExportSelection('48')).toBeNull()
  })
})

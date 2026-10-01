import { describe, expect, it } from 'vitest'

import { getSelectableShiftCodes, getShiftCodeOptions, getShiftCodeWarning, getTodayDateInputValue, isDateRangeInverted, isPreferenceDateInRange, isPreferenceDateValid, isShiftCodeApplicableToDate, normalizePriority, resolveSelectedShiftCode, validatePreferenceForm } from './EmployeePreferencesPage.helpers'

const ALL_SHIFT_CODES = ['THONG_TAM', 'NGOAI_GIO_1', 'NGOAI_GIO_2', 'TRUC_SANG', 'TRUC_CHIEU', 'TRUC_DEM']
const WEEKEND_SHIFT_CODES = ['TRUC_SANG', 'TRUC_CHIEU', 'TRUC_DEM']

describe('isPreferenceDateValid', () => {
  it('accepts a real date', () => {
    expect(isPreferenceDateValid('2026-09-14')).toBe(true)
  })

  it('rejects an empty value', () => {
    expect(isPreferenceDateValid('')).toBe(false)
  })

  it('rejects a non YYYY-MM-DD value', () => {
    expect(isPreferenceDateValid('14/09/2026')).toBe(false)
    expect(isPreferenceDateValid('2026-9-1')).toBe(false)
  })

  it('rejects an impossible day', () => {
    expect(isPreferenceDateValid('2026-02-30')).toBe(false)
    expect(isPreferenceDateValid('2026-13-01')).toBe(false)
  })
})

describe('getSelectableShiftCodes', () => {
  it('excludes weekend-only codes on a weekday', () => {
    const codes = getSelectableShiftCodes('2026-09-14')
    expect(codes).not.toContain('TRUC_CHIEU')
    expect(codes).not.toContain('TRUC_SANG')
    expect(codes).toContain('THONG_TAM')
  })

  it('includes weekend-only codes on a Saturday', () => {
    expect(getSelectableShiftCodes('2026-09-19')).toEqual(WEEKEND_SHIFT_CODES)
  })

  it('includes weekend-only codes on a Sunday', () => {
    expect(getSelectableShiftCodes('2026-09-20')).toEqual(WEEKEND_SHIFT_CODES)
  })

  it('returns every code for a blank date', () => {
    expect(getSelectableShiftCodes('')).toEqual(ALL_SHIFT_CODES)
  })

  it('returns every code for an unparseable date', () => {
    expect(getSelectableShiftCodes('not-a-date')).toEqual(ALL_SHIFT_CODES)
  })
})

describe('resolveSelectedShiftCode', () => {
  it('keeps a still valid selection', () => {
    expect(resolveSelectedShiftCode('2026-09-19', 'TRUC_CHIEU')).toBe('TRUC_CHIEU')
  })

  it('replaces a now invalid selection with the first selectable code', () => {
    expect(resolveSelectedShiftCode('2026-09-14', 'TRUC_CHIEU')).toBe('THONG_TAM')
  })

  it('falls back to the first selectable code when nothing is selected', () => {
    expect(resolveSelectedShiftCode('2026-09-19', '')).toBe('TRUC_SANG')
  })
})

describe('isShiftCodeApplicableToDate', () => {
  it('accepts a weekday code on a weekday', () => {
    expect(isShiftCodeApplicableToDate('2026-09-14', 'THONG_TAM')).toBe(true)
  })

  it('accepts a weekend code on a weekend', () => {
    expect(isShiftCodeApplicableToDate('2026-09-19', 'TRUC_DEM')).toBe(true)
  })

  it('rejects a stored weekend code on a weekday', () => {
    expect(isShiftCodeApplicableToDate('2026-09-14', 'TRUC_DEM')).toBe(false)
  })

  it('rejects a stored weekday code on a weekend', () => {
    expect(isShiftCodeApplicableToDate('2026-09-19', 'THONG_TAM')).toBe(false)
  })

  it('rejects an unknown code', () => {
    expect(isShiftCodeApplicableToDate('2026-09-14', 'KHONG_CO')).toBe(false)
  })

  it('rejects an empty code', () => {
    expect(isShiftCodeApplicableToDate('2026-09-14', '')).toBe(false)
  })
})

describe('getShiftCodeOptions', () => {
  it('returns only the applicable codes when the current code fits', () => {
    expect(getShiftCodeOptions('2026-09-14', 'THONG_TAM')).toEqual(['THONG_TAM', 'NGOAI_GIO_1', 'NGOAI_GIO_2'])
  })

  it('appends an inapplicable stored code so the select keeps showing it', () => {
    expect(getShiftCodeOptions('2026-09-14', 'TRUC_DEM')).toEqual(['THONG_TAM', 'NGOAI_GIO_1', 'NGOAI_GIO_2', 'TRUC_DEM'])
  })

  it('does not duplicate an applicable code', () => {
    expect(getShiftCodeOptions('2026-09-19', 'TRUC_CHIEU')).toEqual(WEEKEND_SHIFT_CODES)
  })

  it('returns only the applicable codes when nothing is selected', () => {
    expect(getShiftCodeOptions('2026-09-14', '')).toEqual(['THONG_TAM', 'NGOAI_GIO_1', 'NGOAI_GIO_2'])
  })
})

describe('getShiftCodeWarning', () => {
  it('warns when the stored code does not apply to the date', () => {
    expect(getShiftCodeWarning('2026-09-14', 'TRUC_DEM')).toContain('TRUC_DEM')
  })

  it('stays empty when the code applies', () => {
    expect(getShiftCodeWarning('2026-09-14', 'THONG_TAM')).toBe('')
  })

  it('stays empty when the code applies on a weekend', () => {
    expect(getShiftCodeWarning('2026-09-19', 'TRUC_SANG')).toBe('')
  })

  it('warns when nothing is selected', () => {
    expect(getShiftCodeWarning('2026-09-14', '')).not.toBe('')
  })

  it('omits the empty code from the warning when nothing is selected', () => {
    const warning = getShiftCodeWarning('2026-09-14', '')
    expect(warning).not.toContain('()')
    expect(warning).toContain('Loại ca đang chọn không áp dụng cho ngày này.')
  })
})

describe('normalizePriority', () => {
  it('parses an integer string', () => {
    expect(normalizePriority('3')).toBe(3)
  })

  it('returns null for a blank value', () => {
    expect(normalizePriority('')).toBeNull()
    expect(normalizePriority('   ')).toBeNull()
  })

  it('returns null for a non integer value', () => {
    expect(normalizePriority('abc')).toBeNull()
    expect(normalizePriority('2.5')).toBeNull()
  })
})

describe('getTodayDateInputValue', () => {
  it('formats a date as YYYY-MM-DD', () => {
    expect(getTodayDateInputValue(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(getTodayDateInputValue(new Date(2026, 11, 31))).toBe('2026-12-31')
  })

  it('returns a YYYY-MM-DD string for the current day', () => {
    expect(getTodayDateInputValue()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})

describe('validatePreferenceForm', () => {
  const validForm = { preferenceDate: '2026-09-14', relatedShiftCode: 'THONG_TAM', priority: '3', note: 'note' }

  it('accepts a valid form', () => {
    expect(validatePreferenceForm(validForm)).toBe('')
  })

  it('rejects a missing date', () => {
    expect(validatePreferenceForm({ ...validForm, preferenceDate: '' })).toBe('Vui lòng chọn ngày áp dụng cho nguyện vọng.')
  })

  it('rejects an unparseable date', () => {
    expect(validatePreferenceForm({ ...validForm, preferenceDate: '14/09/2026' })).toBe('Vui lòng chọn ngày áp dụng cho nguyện vọng.')
  })

  it('rejects a missing shift code', () => {
    expect(validatePreferenceForm({ ...validForm, relatedShiftCode: '' })).toBe('Vui lòng chọn loại ca liên quan.')
  })

  it('rejects a shift code that is not selectable for the date', () => {
    expect(validatePreferenceForm({ ...validForm, relatedShiftCode: 'TRUC_CHIEU' })).toBe('Vui lòng chọn loại ca liên quan.')
  })

  it('accepts a weekend shift code on a weekend date', () => {
    expect(validatePreferenceForm({ ...validForm, preferenceDate: '2026-09-19', relatedShiftCode: 'TRUC_CHIEU' })).toBe('')
  })

  it('rejects a blank note', () => {
    expect(validatePreferenceForm({ ...validForm, note: '   ' })).toBe('Nội dung ghi chú không được bỏ trống.')
  })

  it('rejects an out of range priority', () => {
    expect(validatePreferenceForm({ ...validForm, priority: '0' })).toBe('Mức ưu tiên phải từ 1 đến 5.')
    expect(validatePreferenceForm({ ...validForm, priority: '6' })).toBe('Mức ưu tiên phải từ 1 đến 5.')
  })

  it('accepts the boundary priorities', () => {
    expect(validatePreferenceForm({ ...validForm, priority: '1' })).toBe('')
    expect(validatePreferenceForm({ ...validForm, priority: '5' })).toBe('')
  })

  it('rejects a blank priority', () => {
    expect(validatePreferenceForm({ ...validForm, priority: '' })).toBe('Mức ưu tiên phải từ 1 đến 5.')
  })

  it('reports the date problem first when several problems exist', () => {
    expect(validatePreferenceForm({ preferenceDate: '', relatedShiftCode: '', priority: '9', note: '' })).toBe('Vui lòng chọn ngày áp dụng cho nguyện vọng.')
  })

  it('reports the shift problem before the note and priority problems', () => {
    expect(validatePreferenceForm({ ...validForm, relatedShiftCode: '', note: '', priority: '9' })).toBe('Vui lòng chọn loại ca liên quan.')
  })

  it('reports the note problem before the priority problem', () => {
    expect(validatePreferenceForm({ ...validForm, note: '', priority: '9' })).toBe('Nội dung ghi chú không được bỏ trống.')
  })
})

describe('isPreferenceDateInRange', () => {
  it('matches a date inside the range', () => {
    expect(isPreferenceDateInRange('2026-09-14', '2026-09-10', '2026-09-20')).toBe(true)
  })

  it('matches a date equal to the range bounds', () => {
    expect(isPreferenceDateInRange('2026-09-10', '2026-09-10', '2026-09-20')).toBe(true)
    expect(isPreferenceDateInRange('2026-09-20', '2026-09-10', '2026-09-20')).toBe(true)
  })

  it('does not match a date before the range start', () => {
    expect(isPreferenceDateInRange('2026-09-09', '2026-09-10', '2026-09-20')).toBe(false)
  })

  it('does not match a date after the range end', () => {
    expect(isPreferenceDateInRange('2026-09-21', '2026-09-10', '2026-09-20')).toBe(false)
  })

  it('treats an empty start as unbounded below', () => {
    expect(isPreferenceDateInRange('2026-09-01', '', '2026-09-20')).toBe(true)
    expect(isPreferenceDateInRange('2026-09-25', '', '2026-09-20')).toBe(false)
  })

  it('treats an empty end as unbounded above', () => {
    expect(isPreferenceDateInRange('2026-09-25', '2026-09-10', '')).toBe(true)
    expect(isPreferenceDateInRange('2026-09-01', '2026-09-10', '')).toBe(false)
  })

  it('matches every date when the range is fully empty', () => {
    expect(isPreferenceDateInRange('2026-09-14', '', '')).toBe(true)
  })
})

describe('isDateRangeInverted', () => {
  it('detects a start after the end', () => {
    expect(isDateRangeInverted('2026-09-20', '2026-09-10')).toBe(true)
  })

  it('accepts a start before the end', () => {
    expect(isDateRangeInverted('2026-09-10', '2026-09-20')).toBe(false)
  })

  it('accepts equal start and end', () => {
    expect(isDateRangeInverted('2026-09-10', '2026-09-10')).toBe(false)
  })

  it('accepts an empty range', () => {
    expect(isDateRangeInverted('', '')).toBe(false)
  })

  it('accepts a half filled range', () => {
    expect(isDateRangeInverted('2026-09-10', '')).toBe(false)
    expect(isDateRangeInverted('', '2026-09-20')).toBe(false)
  })
})

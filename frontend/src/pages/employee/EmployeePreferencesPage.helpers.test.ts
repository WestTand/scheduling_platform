import { describe, expect, it } from 'vitest'

import { isDateRangeInverted, isPreferenceDateInRange } from './EmployeePreferencesPage.helpers'

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

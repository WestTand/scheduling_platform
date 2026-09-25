import { describe, expect, test } from 'vitest'
import { getPaginatedSchedules, getTotalSchedulePages } from './SchedulesPage.helpers'

describe('SchedulesPage helpers', () => {
  test('paginates schedules with a fixed page size', () => {
    const schedules = Array.from({ length: 12 }, (_, index) => ({ id: String(index + 1) }))
    expect(getTotalSchedulePages(schedules, 10)).toBe(2)
    expect(getPaginatedSchedules(schedules, 1, 10).map((item) => item.id)).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'])
    expect(getPaginatedSchedules(schedules, 2, 10).map((item) => item.id)).toEqual(['11', '12'])
  })
})

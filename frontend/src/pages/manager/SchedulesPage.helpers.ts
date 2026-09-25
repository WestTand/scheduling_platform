export const SCHEDULES_PAGE_SIZE = 10

export const getTotalSchedulePages = <T>(items: T[], pageSize = SCHEDULES_PAGE_SIZE) => Math.max(1, Math.ceil(items.length / pageSize))

export const getPaginatedSchedules = <T>(items: T[], page: number, pageSize = SCHEDULES_PAGE_SIZE) => {
  const safePage = Math.min(Math.max(page, 1), getTotalSchedulePages(items, pageSize))
  const start = (safePage - 1) * pageSize
  return items.slice(start, start + pageSize)
}

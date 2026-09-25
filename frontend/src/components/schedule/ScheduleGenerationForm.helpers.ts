export const EMPLOYEE_PAGE_SIZE = 9

export const toDateInputValue = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export const addDaysInputValue = (dateValue: string, days: number) => {
  const [year, month, day] = dateValue.split('-').map(Number)
  const date = new Date(year, (month ?? 1) - 1, day)
  date.setDate(date.getDate() + days)
  return toDateInputValue(date)
}

export const getDefaultGenerationDates = (now: Date = new Date()) => {
  const startDate = toDateInputValue(now)
  return { startDate, endDate: addDaysInputValue(startDate, 6) }
}

export const getTotalEmployeePages = <T>(items: T[], pageSize = EMPLOYEE_PAGE_SIZE) => Math.max(1, Math.ceil(items.length / pageSize))

export const getPaginatedEmployees = <T>(items: T[], page: number, pageSize = EMPLOYEE_PAGE_SIZE) => {
  const safePage = Math.min(Math.max(page, 1), getTotalEmployeePages(items, pageSize))
  const start = (safePage - 1) * pageSize
  return items.slice(start, start + pageSize)
}

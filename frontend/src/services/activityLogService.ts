import { activityLogSeed } from '@/mocks/managementData'
import type { ActivityLog, PaginatedResult } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let activityLogs: ActivityLog[] = clone(activityLogSeed)

export const activityLogService = {
  async list({ page = 1, pageSize = 5, search = '', action = 'ALL', startDate = '', endDate = '' }: { page?: number; pageSize?: number; search?: string; action?: string; startDate?: string; endDate?: string }) {
    await delay(250)

    const filtered = activityLogs.filter((log) => {
      const matchesSearch = !search || [log.actorName, log.target, log.description].some((value) => value.toLowerCase().includes(search.toLowerCase()))
      const matchesAction = action === 'ALL' || log.action === action
      const matchesStartDate = !startDate || log.timestamp >= startDate
      const matchesEndDate = !endDate || log.timestamp <= endDate

      return matchesSearch && matchesAction && matchesStartDate && matchesEndDate
    })

    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / pageSize))
    const safePage = Math.min(page, totalPages)
    const startIndex = (safePage - 1) * pageSize

    return {
      data: filtered.slice(startIndex, startIndex + pageSize),
      page: safePage,
      pageSize,
      total,
      totalPages,
    } satisfies PaginatedResult<ActivityLog>
  },

  reset() {
    activityLogs = clone(activityLogSeed)
  },
}

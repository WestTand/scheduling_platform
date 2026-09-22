import { apiConfig } from '@/config/api'
import { leaveRequestSeed, notificationSeed, preferenceSeed, swapRequestSeed } from '@/mocks/managementData'
import { scheduleSeed } from '@/mocks/schedules'
import { requestJson } from '@/services/apiClient'
import type { NavigationSummary } from '@/types/navigationSummary'

const mockSummary = (): NavigationSummary => ({
  pendingLeaveRequests: leaveRequestSeed.filter((item) => item.status === 'PENDING').length,
  pendingPreferences: preferenceSeed.filter((item) => item.status === 'PENDING').length,
  pendingSwapRequests: swapRequestSeed.filter((item) => item.status === 'PENDING_TARGET' || item.status === 'PENDING_APPROVAL').length,
  unreadNotifications: notificationSeed.filter((item) => !item.isRead).length,
  pendingSchedules: scheduleSeed.filter((item) => item.status === 'PENDING_APPROVAL').length,
})

export const navigationSummaryService = {
  async get(): Promise<NavigationSummary> {
    if (apiConfig.useMockApi) {
      return mockSummary()
    }
    return requestJson<NavigationSummary>('/api/me/navigation-summary')
  },
}

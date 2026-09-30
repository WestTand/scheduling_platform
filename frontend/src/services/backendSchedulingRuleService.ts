import { adaptBackendSchedulingRules } from '@/adapters/schedulingRuleAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendSchedulingRulesResponse } from '@/types/backendSchedulingRule'
import type { SchedulingRules } from '@/types/schedulingRule'

const path = '/api/admin/scheduling-rules'

export const backendSchedulingRuleService = {
  async getRules(): Promise<SchedulingRules> {
    const response = await requestJson<BackendSchedulingRulesResponse>(path)
    return adaptBackendSchedulingRules(response)
  },
}

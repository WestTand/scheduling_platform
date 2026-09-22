import { adaptBackendSchedulingRules, toBackendSchedulingRulesRequest } from '@/adapters/schedulingRuleAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendSchedulingRulesResponse } from '@/types/backendSchedulingRule'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

const path = '/api/admin/scheduling-rules'

export const backendSchedulingRuleService = {
  async getRules(): Promise<SchedulingRules> {
    const response = await requestJson<BackendSchedulingRulesResponse>(path)
    return adaptBackendSchedulingRules(response)
  },

  async updateRules(values: SchedulingRulesFormValues): Promise<SchedulingRules> {
    const response = await requestJson<BackendSchedulingRulesResponse>(path, {
      method: 'PUT',
      body: toBackendSchedulingRulesRequest(values),
    })
    return adaptBackendSchedulingRules(response)
  },
}

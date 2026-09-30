import { useEffect, useState } from 'react'

import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { schedulingRuleService } from '@/services/schedulingRuleService'
import { getRuleRows, type RuleSectionKey } from './SchedulingRulesPage.helpers'
import type { SchedulingRules } from '@/types/schedulingRule'

const SECTIONS: { key: RuleSectionKey; title: string; columns: string }[] = [
  { key: 'hardRules', title: 'Quy định bắt buộc', columns: 'md:grid-cols-3' },
  { key: 'quotas', title: 'Hạn mức ca theo tuần', columns: 'md:grid-cols-3' },
  { key: 'softWeights', title: 'Trọng số ưu tiên', columns: 'md:grid-cols-2' },
]

export function SchedulingRulesPage() {
  const [rules, setRules] = useState<SchedulingRules | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      setRules(await schedulingRuleService.getRules())
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải quy định xếp lịch.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const load = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await schedulingRuleService.getRules()
        if (mounted) {
          setRules(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải quy định xếp lịch.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      mounted = false
    }
  }, [])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Quy định xếp lịch"
        description="Xem quy định bắt buộc, hạn mức ca và trọng số ưu tiên dùng cho xếp lịch."
      />

      {loading ? (
        <LoadingState label="Đang tải quy định xếp lịch..." />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchData} />
      ) : !rules ? (
        <ErrorState message="Không có dữ liệu quy định." onRetry={fetchData} />
      ) : (
        SECTIONS.map((section) => (
          <Card key={section.key} className="border-slate-200 bg-white shadow-sm">
            <CardContent className="space-y-4 p-6">
              <h2 className="text-base font-semibold text-slate-900">{section.title}</h2>
              <dl className={`grid gap-4 ${section.columns}`}>
                {getRuleRows(section.key, rules).map((row) => (
                  <div key={row.label} className="rounded-xl border border-slate-200 p-4">
                    <dt className="text-sm text-slate-500">{row.label}</dt>
                    <dd className="mt-1 text-lg font-semibold text-slate-900">{row.value}</dd>
                    {row.hint && <p className="mt-1 text-xs text-slate-500">{row.hint}</p>}
                  </div>
                ))}
              </dl>
            </CardContent>
          </Card>
        ))
      )}
    </div>
  )
}

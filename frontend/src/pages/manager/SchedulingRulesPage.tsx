import { useEffect, useState } from 'react'

import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { schedulingRuleService } from '@/services/schedulingRuleService'
import type { SchedulingRules, SchedulingRulesFormValues } from '@/types/schedulingRule'

interface NumberFieldProps {
  label: string
  value: number
  onChange: (value: number) => void
  min: number
  max: number
  step?: number
  hint?: string
}

function NumberField({ label, value, onChange, min, max, step = 1, hint }: NumberFieldProps) {
  return (
    <div className="space-y-1">
      <label className="text-sm text-slate-700">{label}</label>
      <input
        type="number"
        value={value}
        min={min}
        max={max}
        step={step}
        onChange={(event) => onChange(Number(event.target.value))}
        className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
      />
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

const validateRules = (rules: SchedulingRulesFormValues) => {
  if (rules.hardRules.minRestHoursBetweenShifts < 0 || rules.hardRules.minRestHoursBetweenShifts > 24) return 'Thời gian nghỉ tối thiểu phải từ 0 đến 24 giờ.'
  if (rules.hardRules.maxWorkHoursPerDay < 1 || rules.hardRules.maxWorkHoursPerDay > 24) return 'Số giờ làm tối đa mỗi ngày phải từ 1 đến 24.'
  if (rules.hardRules.maxShiftsPerWeek < 0 || rules.hardRules.maxShiftsPerWeek > 14) return 'Số ca tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.overtimeShiftsPerWeek < 0 || rules.quotas.overtimeShiftsPerWeek > 14) return 'Số ngày ngoài giờ tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.bathingShiftsPerWeek < 0 || rules.quotas.bathingShiftsPerWeek > 14) return 'Số ngày thông tầm tối đa mỗi tuần phải từ 0 đến 14.'
  if (rules.quotas.onCallEveryNDays < 1 || rules.quotas.onCallEveryNDays > 31) return 'Khoảng cách ngày trực phải từ 1 đến 31 ngày.'
  const weights = Object.values(rules.softWeights)
  if (weights.some((value) => value < 0 || value > 999.99)) return 'Trọng số ưu tiên phải từ 0 đến 999.99.'
  return ''
}

export function SchedulingRulesPage() {
  const [formState, setFormState] = useState<SchedulingRulesFormValues | null>(null)
  const [loadedRules, setLoadedRules] = useState<SchedulingRulesFormValues | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [success, setSuccess] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result: SchedulingRules = await schedulingRuleService.getRules()
      setFormState(result)
      setLoadedRules(result)
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
        const result: SchedulingRules = await schedulingRuleService.getRules()
        if (mounted) {
          setFormState(result)
          setLoadedRules(result)
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

  const updateHardRule = (key: keyof SchedulingRulesFormValues['hardRules'], value: number) => {
    setFormState((current) => (current ? { ...current, hardRules: { ...current.hardRules, [key]: value } } : current))
  }

  const updateQuota = (key: keyof SchedulingRulesFormValues['quotas'], value: number) => {
    setFormState((current) => (current ? { ...current, quotas: { ...current.quotas, [key]: value } } : current))
  }

  const updateWeight = (key: keyof SchedulingRulesFormValues['softWeights'], value: number) => {
    setFormState((current) => (current ? { ...current, softWeights: { ...current.softWeights, [key]: value } } : current))
  }

  const handleSave = async () => {
    if (!formState) {
      return
    }

    const validation = validateRules(formState)
    if (validation) {
      setFormError(validation)
      return
    }

    setIsSaving(true)
    setFormError('')
    setSuccess('')

    try {
      const updated = await schedulingRuleService.updateRules(formState)
      setFormState(updated)
      setLoadedRules(updated)
      setSuccess('Đã lưu quy định xếp lịch.')
    } catch (saveError) {
      setFormError(saveError instanceof Error ? saveError.message : 'Không thể lưu quy định xếp lịch.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleReset = () => {
    if (loadedRules) {
      setFormState(JSON.parse(JSON.stringify(loadedRules)) as SchedulingRulesFormValues)
    }
    setFormError('')
    setSuccess('')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quy định xếp lịch" description="Quản lý quy định bắt buộc, hạn mức ca và trọng số ưu tiên dùng cho xếp lịch." />

      {loading ? (
        <LoadingState label="Đang tải quy định xếp lịch..." />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchData} />
      ) : !formState ? (
        <ErrorState message="Không có dữ liệu quy định." onRetry={fetchData} />
      ) : (
        <>
          <Card className="border-slate-200 bg-white shadow-sm">
            <CardContent className="space-y-4 p-6">
              <h2 className="text-base font-semibold text-slate-900">Quy định bắt buộc</h2>
              <div className="grid gap-4 md:grid-cols-3">
                <NumberField label="Giờ nghỉ tối thiểu giữa ca" value={formState.hardRules.minRestHoursBetweenShifts} onChange={(value) => updateHardRule('minRestHoursBetweenShifts', value)} min={0} max={24} />
                <NumberField label="Giờ làm tối đa mỗi ngày" value={formState.hardRules.maxWorkHoursPerDay} onChange={(value) => updateHardRule('maxWorkHoursPerDay', value)} min={1} max={24} />
                <NumberField label="Số ca tối đa mỗi tuần" value={formState.hardRules.maxShiftsPerWeek} onChange={(value) => updateHardRule('maxShiftsPerWeek', value)} min={0} max={14} />
              </div>
            </CardContent>
          </Card>

          <Card className="border-slate-200 bg-white shadow-sm">
            <CardContent className="space-y-4 p-6">
              <h2 className="text-base font-semibold text-slate-900">Hạn mức ca theo tuần</h2>
              <div className="grid gap-4 md:grid-cols-3">
                <NumberField label="Số ngày ngoài giờ tối đa / tuần" value={formState.quotas.overtimeShiftsPerWeek} onChange={(value) => updateQuota('overtimeShiftsPerWeek', value)} min={0} max={14} />
                <NumberField label="Số ngày thông tầm tối đa / tuần" value={formState.quotas.bathingShiftsPerWeek} onChange={(value) => updateQuota('bathingShiftsPerWeek', value)} min={0} max={14} />
                <NumberField label="Khoảng cách tối thiểu giữa ngày trực" value={formState.quotas.onCallEveryNDays} onChange={(value) => updateQuota('onCallEveryNDays', value)} min={1} max={31} hint="Số ngày giãn cách giữa hai ngày trực." />
              </div>
            </CardContent>
          </Card>

          <Card className="border-slate-200 bg-white shadow-sm">
            <CardContent className="space-y-4 p-6">
              <h2 className="text-base font-semibold text-slate-900">Trọng số ưu tiên</h2>
              <div className="grid gap-4 md:grid-cols-2">
                <NumberField label="Công bằng" value={formState.softWeights.fairness} onChange={(value) => updateWeight('fairness', value)} min={0} max={999.99} step={0.01} hint="Ưu tiên phân bổ ca đồng đều." />
                <NumberField label="Nguyện vọng" value={formState.softWeights.preference} onChange={(value) => updateWeight('preference', value)} min={0} max={999.99} step={0.01} hint="Ưu tiên đáp ứng nguyện vọng." />
                <NumberField label="Cuối tuần" value={formState.softWeights.weekend} onChange={(value) => updateWeight('weekend', value)} min={0} max={999.99} step={0.01} hint="Ưu tiên cân bằng ca cuối tuần." />
                <NumberField label="Ca nặng" value={formState.softWeights.heavyShift} onChange={(value) => updateWeight('heavyShift', value)} min={0} max={999.99} step={0.01} hint="Giảm tải ca nặng liên tục." />
                <NumberField label="Cân bằng giờ làm" value={formState.softWeights.workHourBalance} onChange={(value) => updateWeight('workHourBalance', value)} min={0} max={999.99} step={0.01} hint="Ưu tiên tổng giờ làm đồng đều." />
              </div>
            </CardContent>
          </Card>

          <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={handleReset} disabled={isSaving}>
              Khôi phục
            </Button>
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving ? 'Đang lưu...' : 'Lưu quy định'}
            </Button>
          </div>

          {formError && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</div>}
          {success && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{success}</div>}
        </>
      )}
    </div>
  )
}

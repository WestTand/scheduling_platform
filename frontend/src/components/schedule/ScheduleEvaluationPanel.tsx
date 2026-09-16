import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ScheduleEvaluation } from '@/types/schedule'

interface ScheduleEvaluationPanelProps {
  evaluation: ScheduleEvaluation
}

export function ScheduleEvaluationPanel({ evaluation }: ScheduleEvaluationPanelProps) {
  const metrics = [
    ['TotalPenalty', evaluation.totalPenalty],
    ['FairnessPenalty', evaluation.fairnessPenalty],
    ['ConsecutiveShiftPenalty', evaluation.consecutiveShiftPenalty],
    ['WeekendPenalty', evaluation.weekendPenalty],
    ['PreferencePenalty', evaluation.preferencePenalty],
    ['WorkHourImbalance', evaluation.workHourImbalance],
  ]

  return (
    <div className="space-y-4">
      <Card className="border-slate-200 shadow-sm">
        <CardHeader><CardTitle className="text-base">Công thức đánh giá mẫu</CardTitle></CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-xl bg-slate-900 p-4 font-mono text-sm leading-7 text-slate-100">TotalPenalty = w1 x FairnessPenalty + w2 x ConsecutiveShiftPenalty + w3 x WeekendPenalty + w4 x PreferencePenalty + w5 x WorkHourImbalance</div>
          <p className="mt-3 text-sm text-slate-500">Các chỉ số hiện tại là dữ liệu minh họa. Điểm đánh giá thực tế sẽ được trả về từ dịch vụ lập lịch khi tích hợp backend.</p>
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {metrics.map(([label, value]) => (
          <Card key={label} className="border-slate-200 shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">{label}</p><p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p></CardContent></Card>
        ))}
      </div>

      <Card className="border-slate-200 shadow-sm">
        <CardContent className="grid gap-4 p-4 sm:grid-cols-3">
          <div><p className="text-sm text-slate-500">Chênh lệch số ca</p><p className="mt-1 font-semibold text-slate-900">{evaluation.shiftCountGap}</p></div>
          <div><p className="text-sm text-slate-500">Chênh lệch tổng giờ</p><p className="mt-1 font-semibold text-slate-900">{evaluation.workHourGap}</p></div>
          <div><p className="text-sm text-slate-500">Nguyện vọng được đáp ứng</p><p className="mt-1 font-semibold text-slate-900">{evaluation.fulfilledPreferences}</p></div>
        </CardContent>
      </Card>
    </div>
  )
}

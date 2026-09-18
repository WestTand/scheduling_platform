import { WandSparkles } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { ScheduleGenerationForm, type ScheduleGenerationValues } from '@/components/schedule/ScheduleGenerationForm'
import { useState } from 'react'

export function ScheduleGenerationPage() {
  const navigate = useNavigate()
  const [pendingValues, setPendingValues] = useState<ScheduleGenerationValues | null>(null)

  const handleSubmit = (values: ScheduleGenerationValues) => {
    setPendingValues(values)
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Tạo lịch tự động" description="Thiết lập phạm vi và tiêu chí cho một yêu cầu lập lịch mẫu." />
      <div className="flex items-start gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800"><WandSparkles className="mt-0.5 h-5 w-5 shrink-0" /><p>Dữ liệu minh họa. Giao diện này chưa chạy thuật toán, chưa kiểm tra ràng buộc và không tạo phân công mới.</p></div>
      <ScheduleGenerationForm onSubmit={handleSubmit} onCancel={() => navigate('/manager/schedules')} />
      <ConfirmDialog
        open={Boolean(pendingValues)}
        title="Xác nhận tạo lịch mẫu"
        description={`Yêu cầu “${pendingValues?.name}” sẽ mở kết quả lịch mẫu cố định. Không có thuật toán hoặc phân công mới được chạy.`}
        confirmLabel="Xem kết quả mẫu"
        onCancel={() => setPendingValues(null)}
        onConfirm={() => navigate('/manager/schedules/schedule-demo-01')}
      />
    </div>
  )
}

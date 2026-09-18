import { Download, FileText } from 'lucide-react'
import { useState } from 'react'

import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

const exportTypes = [
  { value: 'schedule', label: 'Lịch làm việc' },
  { value: 'employees', label: 'Danh sách nhân viên' },
  { value: 'leaves', label: 'Ngày nghỉ' },
  { value: 'preferences', label: 'Nguyện vọng' },
  { value: 'swap-requests', label: 'Yêu cầu đổi ca' },
  { value: 'schedule-report', label: 'Báo cáo đánh giá lịch' },
]

const formats = [
  { value: 'excel', label: 'Excel' },
  { value: 'csv', label: 'CSV' },
  { value: 'pdf', label: 'PDF' },
]

export function ExportsPage() {
  const [exportType, setExportType] = useState('schedule')
  const [format, setFormat] = useState('excel')
  const [startDate, setStartDate] = useState('2026-09-14')
  const [endDate, setEndDate] = useState('2026-09-20')
  const [status, setStatus] = useState('PUBLISHED')
  const [notice, setNotice] = useState('')

  const handleExport = () => {
    if (!startDate || !endDate) {
      setNotice('Vui lòng chọn khoảng thời gian xuất dữ liệu.')
      return
    }

    setNotice(`Dữ liệu minh họa: đã chuẩn bị xuất ${exportTypes.find((item) => item.value === exportType)?.label ?? 'dữ liệu'} theo định dạng ${formats.find((item) => item.value === format)?.label ?? 'Excel'}. Chức năng backend sẽ được tích hợp sau.`)
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Xuất dữ liệu" description="Tạo cấu hình xuất dữ liệu mock cho báo cáo và tích hợp backend sau này." />

      {notice && <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</div>}

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="grid gap-6 p-5 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-5">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Loại dữ liệu</label>
              <div className="grid gap-2 sm:grid-cols-2">
                {exportTypes.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setExportType(item.value)}
                    className={`rounded-xl border px-3 py-2.5 text-left text-sm transition ${exportType === item.value ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 bg-slate-50 text-slate-700'}`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700">Từ ngày</label>
                <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700">Đến ngày</label>
                <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Trạng thái</label>
              <select value={status} onChange={(event) => setStatus(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
                <option value="ALL">Tất cả</option>
                <option value="DRAFT">Bản nháp</option>
                <option value="PUBLISHED">Đã công bố</option>
                <option value="APPROVED">Đã duyệt</option>
              </select>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Định dạng</label>
              <div className="flex flex-wrap gap-2">
                {formats.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setFormat(item.value)}
                    className={`rounded-xl border px-3 py-2 text-sm ${format === item.value ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 bg-slate-50 text-slate-700'}`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="mb-3 flex items-center gap-2 text-slate-700"><FileText className="h-4 w-4" />Tóm tắt cấu hình xuất</div>
            <div className="space-y-3 text-sm text-slate-600">
              <div className="flex justify-between gap-3"><span>Loại dữ liệu</span><strong>{exportTypes.find((item) => item.value === exportType)?.label}</strong></div>
              <div className="flex justify-between gap-3"><span>Khoảng thời gian</span><strong>{startDate} → {endDate}</strong></div>
              <div className="flex justify-between gap-3"><span>Trạng thái</span><strong>{status}</strong></div>
              <div className="flex justify-between gap-3"><span>Định dạng</span><strong>{formats.find((item) => item.value === format)?.label}</strong></div>
            </div>

            <Button className="mt-5 w-full" onClick={handleExport}><Download className="mr-2 h-4 w-4" />Xuất dữ liệu</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

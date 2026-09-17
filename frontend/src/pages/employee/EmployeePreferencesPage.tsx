import { CalendarRange, Eye, PencilLine, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/common/DataTable'
import { PageHeader } from '@/components/common/PageHeader'
import { SearchInput } from '@/components/common/SearchInput'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

const seedPreferences = [
  { id: 'PREF-001', month: '2026-09', type: 'PREFERRED', relatedShiftType: 'Ca sáng', priority: 1, note: 'Ưu tiên làm ca sáng trong tuần đầu', status: 'PENDING' },
  { id: 'PREF-002', month: '2026-10', type: 'AVOID_SHIFT', relatedShiftType: 'Ca đêm', priority: 2, note: 'Không muốn làm ca đêm nếu có thể', status: 'APPROVED' },
]

const preferenceLabels: Record<string, string> = {
  PREFERRED: 'Muốn làm',
  AVOID: 'Không muốn làm',
  PREFER_SHIFT: 'Ưu tiên ca',
  AVOID_SHIFT: 'Tránh ca',
}

export function EmployeePreferencesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<(typeof seedPreferences)[number] | null>(null)
  const [search, setSearch] = useState('')
  const [monthFilter, setMonthFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [preferences, setPreferences] = useState(seedPreferences)
  const [form, setForm] = useState({ month: '2026-10', type: 'PREFERRED', relatedShiftType: 'Ca sáng', priority: '1', note: '', })

  useEffect(() => {
    if (searchParams.get('action') !== 'create') {
      return
    }

    const nextSearchParams = new URLSearchParams(searchParams)
    nextSearchParams.delete('action')
    setSearchParams(nextSearchParams, { replace: true })
  }, [searchParams, setSearchParams])

  const filtered = useMemo(() => preferences.filter((item) => {
    const matchesSearch = !search || item.note.toLowerCase().includes(search.toLowerCase()) || item.relatedShiftType.toLowerCase().includes(search.toLowerCase())
    const matchesMonth = monthFilter === 'ALL' || item.month === monthFilter
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
    return matchesSearch && matchesMonth && matchesStatus
  }), [preferences, search, monthFilter, statusFilter])

  const handleSubmit = () => {
    if (!form.note.trim()) {
      window.alert('Nội dung ghi chú không được bỏ trống.')
      return
    }

    setPreferences((current) => [{
      id: `PREF-${Date.now()}`,
      month: form.month,
      type: form.type,
      relatedShiftType: form.relatedShiftType,
      priority: Number(form.priority),
      note: form.note,
      status: 'PENDING',
    }, ...current])

    setShowForm(false)
    setForm({ month: '2026-10', type: 'PREFERRED', relatedShiftType: 'Ca sáng', priority: '1', note: '' })
    window.alert('Nguyện vọng đã được gửi và đang chờ xem xét.')
  }

  const handleDelete = (id: string) => {
    setPreferences((current) => current.filter((item) => item.id !== id))
    window.alert('Nguyện vọng đã được hủy trong mock state.')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Đăng ký nguyện vọng" description="Gửi và quản lý nguyện vọng làm việc theo tháng." action={<Button onClick={() => setShowForm(true)}><CalendarRange className="mr-2 h-4 w-4" />Đăng ký nguyện vọng</Button>} />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo ca hoặc ghi chú" onChange={setSearch} />
            <select value={monthFilter} onChange={(event) => setMonthFilter(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả tháng</option>
              <option value="2026-09">09/2026</option>
              <option value="2026-10">10/2026</option>
            </select>
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ xét duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Từ chối</option>
            </select>
          </div>

          <DataTable
            data={filtered}
            columns={[
              { key: 'month', label: 'Tháng', render: (item: (typeof seedPreferences)[number]) => <span>{item.month}</span> },
              { key: 'type', label: 'Loại nguyện vọng', render: (item: (typeof seedPreferences)[number]) => <span>{preferenceLabels[item.type]}</span> },
              { key: 'relatedShiftType', label: 'Ca liên quan' },
              { key: 'priority', label: 'Mức ưu tiên' },
              { key: 'status', label: 'Trạng thái', render: (item: (typeof seedPreferences)[number]) => <StatusBadge status={item.status} /> },
              { key: 'actions', label: 'Thao tác', render: (item: (typeof seedPreferences)[number]) => (
                <div className="flex items-center gap-2">
                  <Button variant="ghost" size="icon" onClick={() => setSelected(item)}><Eye className="h-4 w-4" /></Button>
                  {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => setShowForm(true)}><PencilLine className="h-4 w-4" /></Button>}
                  {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => handleDelete(item.id)}><Trash2 className="h-4 w-4" /></Button>}
                </div>
              ) },
            ]}
          />
        </CardContent>
      </Card>

      <ConfirmDialog
        open={showForm}
        title="Đăng ký nguyện vọng"
        description="Nguyện vọng sẽ được hệ thống xem xét khi tạo lịch nhưng không bảo đảm được đáp ứng hoàn toàn."
        onCancel={() => setShowForm(false)}
        onConfirm={handleSubmit}
        confirmLabel="Gửi nguyện vọng"
      >
        <div className="mt-4 grid gap-4">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Khoảng thời gian áp dụng</label>
            <input type="month" value={form.month} onChange={(event) => setForm((current) => ({ ...current, month: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại nguyện vọng</label>
            <select value={form.type} onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="PREFERRED">Muốn làm</option>
              <option value="AVOID">Không muốn làm</option>
              <option value="PREFER_SHIFT">Ưu tiên ca</option>
              <option value="AVOID_SHIFT">Tránh ca</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại ca liên quan</label>
            <select value={form.relatedShiftType} onChange={(event) => setForm((current) => ({ ...current, relatedShiftType: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="Ca sáng">Ca sáng</option>
              <option value="Ca chiều">Ca chiều</option>
              <option value="Ca đêm">Ca đêm</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Mức ưu tiên</label>
            <input type="number" min={1} max={5} value={form.priority} onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Nội dung ghi chú</label>
            <textarea value={form.note} onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(selected)}
        title="Chi tiết nguyện vọng"
        description="Thông tin chi tiết về nguyện vọng của bạn."
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Tháng</span><strong>{selected.month}</strong></div>
            <div className="flex justify-between"><span>Loại</span><strong>{preferenceLabels[selected.type]}</strong></div>
            <div className="flex justify-between"><span>Ca liên quan</span><strong>{selected.relatedShiftType}</strong></div>
            <div className="flex justify-between"><span>Mức ưu tiên</span><strong>{selected.priority}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selected.note}</strong></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

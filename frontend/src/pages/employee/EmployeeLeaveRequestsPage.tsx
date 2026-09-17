import { CalendarPlus, Eye, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/common/DataTable'
import { PageHeader } from '@/components/common/PageHeader'
import { SearchInput } from '@/components/common/SearchInput'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

const seedRequests = [
  { id: 'LEAVE-001', startDate: '2026-09-18', endDate: '2026-09-18', type: 'Nghỉ phép', status: 'PENDING', reason: 'Đi khám sức khỏe định kỳ', note: 'Chờ quản lý duyệt' },
  { id: 'LEAVE-002', startDate: '2026-09-20', endDate: '2026-09-22', type: 'Nghỉ phép', status: 'APPROVED', reason: 'Đi du lịch gia đình', note: 'Đã duyệt theo quy định' },
  { id: 'LEAVE-003', startDate: '2026-09-25', endDate: '2026-09-25', type: 'Nghỉ không lương', status: 'REJECTED', reason: 'Có việc cá nhân', note: 'Không đủ nhân sự' },
]

export function EmployeeLeaveRequestsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<(typeof seedRequests)[number] | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [requests, setRequests] = useState(seedRequests)
  const [form, setForm] = useState({ startDate: '2026-09-27', endDate: '2026-09-28', type: 'Nghỉ phép', reason: '', note: '' })
  const [error, setError] = useState('')

  useEffect(() => {
    if (searchParams.get('action') !== 'create') {
      return
    }

    const nextSearchParams = new URLSearchParams(searchParams)
    nextSearchParams.delete('action')
    setSearchParams(nextSearchParams, { replace: true })
  }, [searchParams, setSearchParams])

  const filtered = useMemo(() => requests.filter((item) => {
    const matchesSearch = !search || item.reason.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
    return matchesSearch && matchesStatus
  }), [requests, search, statusFilter])

  const handleSubmit = () => {
    if (!form.startDate || !form.endDate || !form.reason.trim()) {
      setError('Ngày bắt đầu/kết thúc và lý do không được bỏ trống.')
      return
    }

    if (form.startDate > form.endDate) {
      setError('Ngày bắt đầu không được lớn hơn ngày kết thúc.')
      return
    }

    setRequests((current) => [{
      id: `LEAVE-${Date.now()}`,
      startDate: form.startDate,
      endDate: form.endDate,
      type: form.type,
      status: 'PENDING',
      reason: form.reason,
      note: form.note || 'Yêu cầu đang chờ quản lý duyệt',
    }, ...current])

    setForm({ startDate: '2026-09-27', endDate: '2026-09-28', type: 'Nghỉ phép', reason: '', note: '' })
    setShowForm(false)
    setError('')
    window.alert('Đã tạo yêu cầu nghỉ thành công. Yêu cầu đang chờ quản lý duyệt.')
  }

  const handleCancel = (id: string) => {
    setRequests((current) => current.map((item) => item.id === id ? { ...item, status: 'REJECTED', note: 'Nhân viên hủy yêu cầu' } : item))
    window.alert('Yêu cầu đã được hủy trong mock state.')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Đăng ký ngày nghỉ" description="Theo dõi các yêu cầu nghỉ phép của bạn và trạng thái xử lý." action={<Button onClick={() => setShowForm(true)}><CalendarPlus className="mr-2 h-4 w-4" />Đăng ký ngày nghỉ</Button>} />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm kiếm theo lý do" onChange={setSearch} />
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Từ chối</option>
            </select>
          </div>

          <DataTable
            data={filtered}
            columns={[
              { key: 'id', label: 'Mã yêu cầu' },
              { key: 'startDate', label: 'Từ ngày', render: (item: (typeof seedRequests)[number]) => <span>{new Date(item.startDate).toLocaleDateString('vi-VN')}</span> },
              { key: 'endDate', label: 'Đến ngày', render: (item: (typeof seedRequests)[number]) => <span>{new Date(item.endDate).toLocaleDateString('vi-VN')}</span> },
              { key: 'type', label: 'Loại nghỉ' },
              { key: 'status', label: 'Trạng thái', render: (item: (typeof seedRequests)[number]) => <StatusBadge status={item.status} /> },
              { key: 'actions', label: 'Thao tác', render: (item: (typeof seedRequests)[number]) => (
                <div className="flex items-center gap-2">
                  <Button variant="ghost" size="icon" onClick={() => setSelected(item)}><Eye className="h-4 w-4" /></Button>
                  {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => handleCancel(item.id)}><X className="h-4 w-4" /></Button>}
                </div>
              ) },
            ]}
          />
        </CardContent>
      </Card>

      <ConfirmDialog
        open={showForm}
        title="Đăng ký ngày nghỉ"
        description="Yêu cầu của bạn sẽ được gửi tới quản lý để xét duyệt."
        onCancel={() => { setShowForm(false); setError('') }}
        onConfirm={handleSubmit}
        confirmLabel="Gửi yêu cầu"
      >
        <div className="mt-4 grid gap-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1">
              <label className="text-sm text-slate-700">Ngày bắt đầu</label>
              <input type="date" value={form.startDate} onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
            </div>
            <div className="space-y-1">
              <label className="text-sm text-slate-700">Ngày kết thúc</label>
              <input type="date" value={form.endDate} onChange={(event) => setForm((current) => ({ ...current, endDate: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại nghỉ</label>
            <select value={form.type} onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="Nghỉ phép">Nghỉ phép</option>
              <option value="Nghỉ không lương">Nghỉ không lương</option>
              <option value="Nghỉ ốm">Nghỉ ốm</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Lý do</label>
            <textarea value={form.reason} onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ghi chú</label>
            <textarea value={form.note} onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} rows={2} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(selected)}
        title="Chi tiết yêu cầu nghỉ"
        description="Thông tin yêu cầu nghỉ hiện tại của bạn."
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã yêu cầu</span><strong>{selected.id}</strong></div>
            <div className="flex justify-between"><span>Ngày bắt đầu</span><strong>{new Date(selected.startDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Ngày kết thúc</span><strong>{new Date(selected.endDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Loại nghỉ</span><strong>{selected.type}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selected.reason}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selected.note}</strong></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

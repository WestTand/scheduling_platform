import { CalendarClock, Eye, X } from 'lucide-react'
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
  { id: 'SWAP-001', currentShift: 'Ca sáng • 07:00 - 12:00', requestedShift: 'Ca chiều • 13:00 - 18:00', requestedDate: '2026-09-17', partner: 'Lê Thị Hòa', reason: 'Có lịch học vào buổi sáng', note: 'Chờ quản lý duyệt', status: 'PENDING' },
  { id: 'SWAP-002', currentShift: 'Ca chiều • 13:00 - 18:00', requestedShift: 'Ca sáng • 07:00 - 12:00', requestedDate: '2026-09-18', partner: 'Trần Văn Nam', reason: 'Muốn đổi để bù thời gian gia đình', note: 'Đã duyệt', status: 'APPROVED' },
]

export function EmployeeSwapRequestsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<(typeof seedRequests)[number] | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [requests, setRequests] = useState(seedRequests)
  const [form, setForm] = useState({ currentShift: 'Ca sáng • 07:00 - 12:00', requestedShift: 'Ca chiều • 13:00 - 18:00', partner: 'Lê Thị Hòa', reason: '', note: '' })

  useEffect(() => {
    if (searchParams.get('action') !== 'create') {
      return
    }

    const nextSearchParams = new URLSearchParams(searchParams)
    nextSearchParams.delete('action')
    setSearchParams(nextSearchParams, { replace: true })
  }, [searchParams, setSearchParams])

  const filtered = useMemo(() => requests.filter((item) => {
    const matchesSearch = !search || item.reason.toLowerCase().includes(search.toLowerCase()) || item.partner.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
    return matchesSearch && matchesStatus
  }), [requests, search, statusFilter])

  const handleSubmit = () => {
    if (!form.currentShift || !form.reason.trim()) {
      window.alert('Phải chọn ca hiện tại và nhập lý do.')
      return
    }

    if (form.currentShift === form.requestedShift) {
      window.alert('Không được chọn lại chính ca hiện tại làm ca muốn đổi.')
      return
    }

    setRequests((current) => [{
      id: `SWAP-${Date.now()}`,
      currentShift: form.currentShift,
      requestedShift: form.requestedShift,
      requestedDate: '2026-09-21',
      partner: form.partner,
      reason: form.reason,
      note: 'Yêu cầu phải được quản lý phê duyệt',
      status: 'PENDING',
    }, ...current])

    setShowForm(false)
    setForm({ currentShift: 'Ca sáng • 07:00 - 12:00', requestedShift: 'Ca chiều • 13:00 - 18:00', partner: 'Lê Thị Hòa', reason: '', note: '' })
    window.alert('Yêu cầu đổi ca đã được tạo và đang chờ quản lý phê duyệt.')
  }

  const handleCancel = (id: string) => {
    setRequests((current) => current.map((item) => item.id === id ? { ...item, status: 'REJECTED', note: 'Nhân viên hủy yêu cầu' } : item))
    window.alert('Yêu cầu đã được hủy trong mock state.')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Yêu cầu đổi ca" description="Gửi và theo dõi yêu cầu đổi ca của bạn." action={<Button onClick={() => setShowForm(true)}><CalendarClock className="mr-2 h-4 w-4" />Tạo yêu cầu đổi ca</Button>} />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo lý do hoặc nhân viên đổi cùng" onChange={setSearch} />
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ xử lý</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Từ chối</option>
            </select>
          </div>

          <DataTable
            data={filtered}
            columns={[
              { key: 'id', label: 'Mã yêu cầu' },
              { key: 'currentShift', label: 'Ca hiện tại' },
              { key: 'requestedShift', label: 'Ca muốn đổi' },
              { key: 'partner', label: 'Nhân viên đổi cùng' },
              { key: 'requestedDate', label: 'Ngày mong muốn' },
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
        title="Tạo yêu cầu đổi ca"
        description="Yêu cầu sẽ được gửi để quản lý xem xét. Không tự động đổi lịch sau khi gửi." 
        onCancel={() => setShowForm(false)}
        onConfirm={handleSubmit}
        confirmLabel="Gửi yêu cầu"
      >
        <div className="mt-4 grid gap-4">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ca hiện tại của bạn</label>
            <select value={form.currentShift} onChange={(event) => setForm((current) => ({ ...current, currentShift: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="Ca sáng • 07:00 - 12:00">Ca sáng • 07:00 - 12:00</option>
              <option value="Ca chiều • 13:00 - 18:00">Ca chiều • 13:00 - 18:00</option>
              <option value="Ca đêm • 19:00 - 23:00">Ca đêm • 19:00 - 23:00</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ca muốn đổi</label>
            <select value={form.requestedShift} onChange={(event) => setForm((current) => ({ ...current, requestedShift: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="Ca chiều • 13:00 - 18:00">Ca chiều • 13:00 - 18:00</option>
              <option value="Ca sáng • 07:00 - 12:00">Ca sáng • 07:00 - 12:00</option>
              <option value="Ca đêm • 19:00 - 23:00">Ca đêm • 19:00 - 23:00</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Nhân viên đổi cùng nếu có</label>
            <select value={form.partner} onChange={(event) => setForm((current) => ({ ...current, partner: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="Lê Thị Hòa">Lê Thị Hòa</option>
              <option value="Trần Văn Nam">Trần Văn Nam</option>
              <option value="Hoàng Văn Tâm">Hoàng Văn Tâm</option>
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
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(selected)}
        title="Chi tiết yêu cầu đổi ca"
        description="Thông tin chi tiết về yêu cầu đổi ca của bạn."
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã yêu cầu</span><strong>{selected.id}</strong></div>
            <div className="flex justify-between"><span>Ca hiện tại</span><strong>{selected.currentShift}</strong></div>
            <div className="flex justify-between"><span>Ca muốn đổi</span><strong>{selected.requestedShift}</strong></div>
            <div className="flex justify-between"><span>Nhân viên đổi cùng</span><strong>{selected.partner}</strong></div>
            <div className="flex justify-between"><span>Ngày mong muốn</span><strong>{selected.requestedDate}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selected.reason}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

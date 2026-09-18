import { BellRing, CheckCheck, Eye } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/common/DataTable'
import { PageHeader } from '@/components/common/PageHeader'
import { SearchInput } from '@/components/common/SearchInput'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

const notifications = [
  { id: 'NOTI-001', title: 'Lịch làm việc tuần tới đã cập nhật', content: 'Bạn cần kiểm tra lịch làm việc mới được bổ sung từ quản lý.', type: 'Lịch làm việc', createdAt: '2026-09-15T08:00:00', isRead: false },
  { id: 'NOTI-002', title: 'Yêu cầu đổi ca đang chờ duyệt', content: 'Yêu cầu đổi ca của bạn đã được gửi cho quản lý. Vui lòng chờ xác nhận.', type: 'Đổi ca', createdAt: '2026-09-14T15:15:00', isRead: true },
  { id: 'NOTI-003', title: 'Quy định nghỉ phép mới', content: 'Quy trình nghỉ phép trong tháng 09/2026 đã được cập nhật.', type: 'Ngày nghỉ', createdAt: '2026-09-12T10:30:00', isRead: true },
]

export function EmployeeNotificationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [selected, setSelected] = useState<(typeof notifications)[number] | null>(() => notifications.find((item) => item.id === searchParams.get('notificationId')) ?? null)

  const closeDetails = () => {
    setSelected(null)
    const params = new URLSearchParams(searchParams)
    params.delete('notificationId')
    setSearchParams(params, { replace: true })
  }

  const filtered = useMemo(() => notifications.filter((item) => {
    const matchesSearch = !search || item.title.toLowerCase().includes(search.toLowerCase()) || item.content.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === 'ALL' || (statusFilter === 'UNREAD' ? !item.isRead : item.isRead)
    return matchesSearch && matchesStatus
  }), [search, statusFilter])

  const handleMarkRead = (id: string) => {
    const target = notifications.find((item) => item.id === id)
    if (!target) return
    target.isRead = true
    closeDetails()
    window.alert('Thông báo đã được đánh dấu là đã đọc.')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Thông báo của tôi" description="Theo dõi các cập nhật quan trọng liên quan đến lịch, nghỉ phép, và yêu cầu đổi ca." />

      <section className="grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Tổng</p><p className="mt-2 text-2xl font-semibold">{notifications.length}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Chưa đọc</p><p className="mt-2 text-2xl font-semibold">{notifications.filter((item) => !item.isRead).length}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Đã đọc</p><p className="mt-2 text-2xl font-semibold">{notifications.filter((item) => item.isRead).length}</p></div>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm kiếm thông báo" onChange={setSearch} />
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả</option>
              <option value="UNREAD">Chưa đọc</option>
              <option value="READ">Đã đọc</option>
            </select>
          </div>

          <DataTable
            data={filtered}
            columns={[
              { key: 'title', label: 'Tiêu đề' },
              { key: 'type', label: 'Loại' },
              { key: 'createdAt', label: 'Thời gian', render: (item: (typeof notifications)[number]) => <span>{new Date(item.createdAt).toLocaleString('vi-VN')}</span> },
              { key: 'isRead', label: 'Trạng thái', render: (item: (typeof notifications)[number]) => <StatusBadge status={item.isRead ? 'APPROVED' : 'PENDING'} /> },
              { key: 'actions', label: 'Thao tác', render: (item: (typeof notifications)[number]) => (
                <div className="flex items-center gap-2">
                  <Button variant="ghost" size="icon" onClick={() => { setSelected(item); const params = new URLSearchParams(searchParams); params.set('notificationId', item.id); setSearchParams(params) }} aria-label="Xem chi tiết thông báo"><Eye className="h-4 w-4" /></Button>
                  {!item.isRead && <Button variant="ghost" size="icon" onClick={() => handleMarkRead(item.id)}><CheckCheck className="h-4 w-4" /></Button>}
                </div>
              ) },
            ]}
          />
        </CardContent>
      </Card>

      <ConfirmDialog
        open={Boolean(selected)}
        title={selected?.title ?? 'Chi tiết thông báo'}
        description={selected?.content ?? 'Thông tin chi tiết về thông báo.'}
        onCancel={closeDetails}
        onConfirm={closeDetails}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Loại</span><strong>{selected.type}</strong></div>
            <div className="flex justify-between"><span>Thời gian</span><strong>{new Date(selected.createdAt).toLocaleString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.isRead ? 'APPROVED' : 'PENDING'} /></div>
            {!selected.isRead && <Button variant="outline" onClick={() => handleMarkRead(selected.id)} className="mt-2 w-full"><BellRing className="mr-2 h-4 w-4" />Đánh dấu đã đọc</Button>}
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

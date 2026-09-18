import { Bell, CheckCheck, Eye } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

import { DataTable } from '@/components/common/DataTable'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { SearchInput } from '@/components/common/SearchInput'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { notificationService } from '@/services/notificationService'
import type { NotificationItem, PaginatedResult } from '@/types/management'

const typeOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'SCHEDULE', label: 'Lịch làm việc' },
  { value: 'LEAVE', label: 'Ngày nghỉ' },
  { value: 'PREFERENCE', label: 'Nguyện vọng' },
  { value: 'SWAP', label: 'Đổi ca' },
  { value: 'GENERAL', label: 'Chung' },
]

export function NotificationsPage() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [type, setType] = useState('ALL')
  const [status, setStatus] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<NotificationItem>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [selected, setSelected] = useState<NotificationItem | null>(null)
  const [success, setSuccess] = useState('')

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await notificationService.list({ page, pageSize: 5, search, type, status })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải thông báo.')
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
        const result = await notificationService.list({ page, pageSize: 5, search, type, status })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải thông báo.')
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
  }, [page, search, type, status])

  const stats = useMemo(() => ({
    all: data.total,
    unread: data.data.filter((item) => !item.isRead).length,
    read: data.data.filter((item) => item.isRead).length,
  }), [data])

  const handleMarkRead = async (id: string) => {
    try {
      await notificationService.markRead(id)
      setSuccess('Thông báo đã được đánh dấu là đã đọc.')
      await fetchData()
    } catch (errorMessage) {
      window.alert(errorMessage instanceof Error ? errorMessage.message : 'Không thể đánh dấu đã đọc.')
    }
  }

  const handleCreate = () => {
    setSuccess('Mock state: thông báo mới đã được tạo và hiển thị trong giao diện.')
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý thông báo" description="Theo dõi và tạo thông báo cho nhân viên trong mock state." />

      <section className="grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Tất cả</p><p className="mt-2 text-2xl font-semibold">{stats.all}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Chưa đọc</p><p className="mt-2 text-2xl font-semibold">{stats.unread}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Đã đọc</p><p className="mt-2 text-2xl font-semibold">{stats.read}</p></div>
      </section>

      {success && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{success}</div>}

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center">
            <div className="flex-1"><SearchInput value={search} placeholder="Tìm kiếm tiêu đề hoặc nội dung" onChange={setSearch} /></div>
            <select value={type} onChange={(event) => { setType(event.target.value); setPage(1) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              {typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả</option>
              <option value="UNREAD">Chưa đọc</option>
              <option value="READ">Đã đọc</option>
            </select>
            <Button variant="outline" onClick={() => { setSuccess('Mock state: tất cả thông báo đã được đánh dấu đã đọc.'); void notificationService.markAllRead().then(() => fetchData()) }}><CheckCheck className="mr-2 h-4 w-4" />Đánh dấu tất cả đã đọc</Button>
          </div>

          <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-4">
            <p className="mb-3 text-sm font-medium text-slate-700">Tạo thông báo</p>
            <div className="grid gap-3 md:grid-cols-2">
              <input className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm" placeholder="Tiêu đề thông báo" defaultValue="Thông báo lịch mới" />
              <input className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm" placeholder="Đối tượng nhận" defaultValue="Tất cả nhân viên" />
              <select className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm" defaultValue="GENERAL">
                <option value="GENERAL">Chung</option>
                <option value="SCHEDULE">Lịch làm việc</option>
                <option value="LEAVE">Ngày nghỉ</option>
                <option value="PREFERENCE">Nguyện vọng</option>
                <option value="SWAP">Đổi ca</option>
              </select>
              <select className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm" defaultValue="MEDIUM">
                <option value="LOW">Thấp</option>
                <option value="MEDIUM">Trung bình</option>
                <option value="HIGH">Cao</option>
              </select>
            </div>
            <textarea className="mt-3 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm" rows={3} defaultValue="Lịch làm việc tuần tới đã được cập nhật. Vui lòng kiểm tra trước khi làm việc." />
            <div className="mt-3 flex justify-end"><Button onClick={handleCreate}><Bell className="mr-2 h-4 w-4" />Tạo thông báo</Button></div>
          </div>

          {loading ? (
            <LoadingState label="Đang tải thông báo..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có thông báo" description="Chưa có dòng thông báo phù hợp với bộ lọc." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'title', label: 'Tiêu đề' },
                  { key: 'type', label: 'Loại', render: (item: NotificationItem) => <span>{typeOptions.find((option) => option.value === item.type)?.label ?? item.type}</span> },
                  { key: 'createdAt', label: 'Thời gian', render: (item: NotificationItem) => <span>{new Date(item.createdAt).toLocaleString('vi-VN')}</span> },
                  { key: 'isRead', label: 'Trạng thái', render: (item: NotificationItem) => <StatusBadge status={item.isRead ? 'APPROVED' : 'PENDING'} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: NotificationItem) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelected(item)}><Eye className="h-4 w-4" /></Button>
                      {!item.isRead && <Button variant="ghost" size="icon" onClick={() => handleMarkRead(item.id)}><CheckCheck className="h-4 w-4" /></Button>}
                    </div>
                  ) },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={Boolean(selected)}
        title={selected?.title ?? 'Chi tiết thông báo'}
        description={selected?.content ?? 'Đây là thông báo mẫu.'}
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Loại</span><strong>{typeOptions.find((option) => option.value === selected.type)?.label ?? selected.type}</strong></div>
            <div className="flex justify-between"><span>Đối tượng</span><strong>{selected.recipientType === 'ALL' ? 'Tất cả nhân viên' : selected.recipient}</strong></div>
            <div className="flex justify-between"><span>Thời gian gửi</span><strong>{new Date(selected.createdAt).toLocaleString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.isRead ? 'APPROVED' : 'PENDING'} /></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

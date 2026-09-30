import { BellRing, CheckCheck, Eye } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { useAppDialog } from '@/components/common/AppDialogContext'
import { DataTable } from '@/components/common/DataTable'
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

export function EmployeeNotificationsPage() {
  const { showDialog } = useAppDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<NotificationItem>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [selected, setSelected] = useState<NotificationItem | null>(null)

  const closeDetails = () => {
    setSelected(null)
    const params = new URLSearchParams(searchParams)
    params.delete('notificationId')
    setSearchParams(params, { replace: true })
  }

  const fetchData = async () => {
    setLoading(true)
    setError('')
    try {
      const result = await notificationService.list({ page, pageSize: 5, search, status: statusFilter })
      setData(result)
      const selectedId = searchParams.get('notificationId')
      setSelected(selectedId ? result.data.find((item) => item.id === selectedId) ?? null : null)
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
        const result = await notificationService.list({ page, pageSize: 5, search, status: statusFilter })
        if (mounted) {
          setData(result)
          const selectedId = searchParams.get('notificationId')
          setSelected(selectedId ? result.data.find((item) => item.id === selectedId) ?? null : null)
        }
      } catch (loadError) {
        if (mounted) setError(loadError instanceof Error ? loadError.message : 'Không thể tải thông báo.')
      } finally {
        if (mounted) setLoading(false)
      }
    }
    void load()
    return () => { mounted = false }
  }, [page, search, statusFilter, searchParams])

  const stats = useMemo(() => ({
    total: data.total,
    unread: data.data.filter((item) => !item.isRead).length,
    read: data.data.filter((item) => item.isRead).length,
  }), [data])

  const handleMarkRead = async (id: string) => {
    try {
      await notificationService.markRead(id)
      closeDetails()
      await fetchData()
    } catch (markError) {
      showDialog({ variant: 'error', description: markError instanceof Error ? markError.message : 'Không thể đánh dấu đã đọc.' })
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Thông báo của tôi" description="Theo dõi các cập nhật quan trọng liên quan đến lịch, nghỉ phép, và yêu cầu đổi ca." />

      <section className="grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Tổng</p><p className="mt-2 text-2xl font-semibold">{stats.total}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Chưa đọc</p><p className="mt-2 text-2xl font-semibold">{stats.unread}</p></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-sm text-slate-500">Đã đọc</p><p className="mt-2 text-2xl font-semibold">{stats.read}</p></div>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm kiếm thông báo" onChange={setSearch} />
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả</option>
              <option value="UNREAD">Chưa đọc</option>
              <option value="READ">Đã đọc</option>
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải thông báo..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có thông báo" description="Chưa có thông báo phù hợp với bộ lọc." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'title', label: 'Tiêu đề' },
                  { key: 'type', label: 'Loại' },
                  { key: 'createdAt', label: 'Thời gian', render: (item: NotificationItem) => <span>{new Date(item.createdAt).toLocaleString('vi-VN')}</span> },
                  { key: 'isRead', label: 'Trạng thái', render: (item: NotificationItem) => <StatusBadge status={item.isRead ? 'APPROVED' : 'PENDING'} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: NotificationItem) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => { setSelected(item); const params = new URLSearchParams(searchParams); params.set('notificationId', item.id); setSearchParams(params) }} aria-label="Xem chi tiết thông báo"><Eye className="h-4 w-4" /></Button>
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

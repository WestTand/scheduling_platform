import { CalendarPlus, Eye, X } from 'lucide-react'
import { useEffect, useState } from 'react'
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
import { getLeaveSubmitErrorDialogMessage } from '@/pages/employee/EmployeeLeaveRequestsPage.helpers'
import { leaveRequestService } from '@/services/leaveRequestService'
import type { LeaveRequest, PaginatedResult } from '@/types/management'

export function EmployeeLeaveRequestsPage() {
  const { showDialog } = useAppDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<LeaveRequest | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [form, setForm] = useState({ startDate: '2026-09-27', endDate: '2026-09-28', type: 'Nghỉ phép', reason: '', note: '' })
  const [error, setError] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [data, setData] = useState<PaginatedResult<LeaveRequest>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })

  const fetchData = async () => {
    setLoading(true)
    setLoadError('')

    try {
      const result = await leaveRequestService.listMine({ page, pageSize: 5, search, status: statusFilter })
      setData(result)
    } catch (loadErr) {
      setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải yêu cầu nghỉ.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (searchParams.get('action') !== 'create') {
      return
    }

    const nextSearchParams = new URLSearchParams(searchParams)
    nextSearchParams.delete('action')
    setSearchParams(nextSearchParams, { replace: true })
  }, [searchParams, setSearchParams])

  useEffect(() => {
    let mounted = true

    const loadRequests = async () => {
      setLoading(true)
      setLoadError('')

      try {
        const result = await leaveRequestService.listMine({ page, pageSize: 5, search, status: statusFilter })
        if (mounted) {
          setData(result)
        }
      } catch (loadErr) {
        if (mounted) {
          setLoadError(loadErr instanceof Error ? loadErr.message : 'Không thể tải yêu cầu nghỉ.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadRequests()

    return () => {
      mounted = false
    }
  }, [page, search, statusFilter])

  const handleSubmit = async () => {
    if (!form.startDate || !form.endDate || !form.reason.trim()) {
      setError('Ngày bắt đầu/kết thúc và lý do không được bỏ trống.')
      return
    }

    if (form.startDate > form.endDate) {
      setError('Ngày bắt đầu không được lớn hơn ngày kết thúc.')
      return
    }

    try {
      await leaveRequestService.createMine({ startDate: form.startDate, endDate: form.endDate, reason: form.reason, note: form.note, type: form.type })

      setForm({ startDate: '2026-09-27', endDate: '2026-09-28', type: 'Nghỉ phép', reason: '', note: '' })
      setShowForm(false)
      setError('')
      setPage(1)
      await fetchData()
      showDialog({ variant: 'success', description: 'Đã tạo yêu cầu nghỉ thành công. Yêu cầu đang chờ quản lý duyệt.' })
    } catch (submitError) {
      const message = submitError instanceof Error ? submitError.message : ''
      const dialogMessage = getLeaveSubmitErrorDialogMessage(message)
      setError(dialogMessage)
      showDialog({ title: 'Không thể tạo ngày nghỉ', variant: 'error', description: dialogMessage })
    }
  }

  const handleCancel = async (id: string) => {
    try {
      await leaveRequestService.cancelMine(id)
      await fetchData()
    } catch (cancelError) {
      showDialog({ variant: 'error', description: cancelError instanceof Error ? cancelError.message : 'Không thể hủy yêu cầu.' })
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Đăng ký ngày nghỉ" description="Theo dõi các yêu cầu nghỉ phép của bạn và trạng thái xử lý." action={<Button onClick={() => setShowForm(true)}><CalendarPlus className="mr-2 h-4 w-4" />Đăng ký ngày nghỉ</Button>} />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm kiếm theo lý do" onChange={(value) => { setSearch(value); setPage(1) }} />
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Từ chối</option>
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải yêu cầu nghỉ..." />
          ) : loadError ? (
            <ErrorState message={loadError} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có yêu cầu nghỉ" description="Bạn chưa có yêu cầu nào phù hợp với bộ lọc." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'id', label: 'Mã yêu cầu' },
                  { key: 'startDate', label: 'Từ ngày', render: (item: LeaveRequest) => <span>{new Date(item.startDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'endDate', label: 'Đến ngày', render: (item: LeaveRequest) => <span>{new Date(item.endDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'type', label: 'Loại nghỉ', render: (item: LeaveRequest) => <span>{(item as LeaveRequest & { type?: string }).type ?? 'Nghỉ phép'}</span> },
                  { key: 'status', label: 'Trạng thái', render: (item: LeaveRequest) => <StatusBadge status={item.status} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: LeaveRequest) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelected(item)}><Eye className="h-4 w-4" /></Button>
                      {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => handleCancel(item.id)}><X className="h-4 w-4" /></Button>}
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
            <div className="flex justify-between"><span>Loại nghỉ</span><strong>{(selected as LeaveRequest & { type?: string }).type ?? 'Nghỉ phép'}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selected.reason}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selected.managerNote ?? '—'}</strong></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

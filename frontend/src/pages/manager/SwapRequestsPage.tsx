import { Check, Clock3, Eye, Search, X } from 'lucide-react'
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
import { StatCard } from '@/components/common/StatCard'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { apiConfig } from '@/config/api'
import { formatShiftRangeLabel, getShiftDefinition } from '@/configs/shiftDefinitions'
import { swapRequestService } from '@/services/swapRequestService'
import type { PaginatedResult, SwapRequest, SwapRequestStatus } from '@/types/management'

const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING_TARGET', label: 'Chờ nhân viên phản hồi' },
  { value: 'PENDING_APPROVAL', label: 'Chờ quản lý duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export function SwapRequestsPage() {
  const { showDialog } = useAppDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState(() => searchParams.get('status') ?? 'ALL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<SwapRequest>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [selected, setSelected] = useState<SwapRequest | null>(null)
  const [decisionDialog, setDecisionDialog] = useState<{ id: string; type: 'APPROVED' | 'REJECTED' } | null>(null)
  const [managerNote, setManagerNote] = useState('')

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await swapRequestService.list({ page, pageSize: 5, search, status, startDate, endDate })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải yêu cầu đổi ca.')
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
        const result = await swapRequestService.list({ page, pageSize: 5, search, status, startDate, endDate })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải yêu cầu đổi ca.')
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
  }, [page, search, status, startDate, endDate])

  const stats = useMemo(() => ({
    pending: data.data.filter((item) => item.status === 'PENDING_TARGET' || item.status === 'PENDING_APPROVAL').length,
    approved: data.data.filter((item) => item.status === 'APPROVED').length,
    rejected: data.data.filter((item) => item.status === 'REJECTED').length,
    cancelled: data.data.filter((item) => item.status === 'CANCELLED').length,
  }), [data])

  const handleDecision = async () => {
    if (!decisionDialog) {
      return
    }

    if (decisionDialog.type === 'REJECTED' && apiConfig.useMockApi && !managerNote.trim()) {
      showDialog({ variant: 'warning', description: 'Vui lòng nhập lý do từ chối.' })
      return
    }

    try {
      await swapRequestService.updateStatus(decisionDialog.id, decisionDialog.type as SwapRequestStatus, managerNote)
      setDecisionDialog(null)
      setSelected(null)
      await fetchData()
    } catch (decisionError) {
      showDialog({ variant: 'error', description: decisionError instanceof Error ? decisionError.message : 'Không thể cập nhật yêu cầu đổi ca.' })
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý yêu cầu đổi ca" description="Theo dõi và xử lý các yêu cầu đổi ca của nhân viên." />

      <section className="grid gap-4 md:grid-cols-4">
        <StatCard label="Chờ xử lý" value={stats.pending} icon={<Clock3 className="h-5 w-5" />} tone="amber" />
        <StatCard label="Đã duyệt" value={stats.approved} icon={<Check className="h-5 w-5" />} tone="green" />
        <StatCard label="Đã từ chối" value={stats.rejected} icon={<X className="h-5 w-5" />} tone="red" />
        <StatCard label="Đã hủy" value={stats.cancelled} icon={<Search className="h-5 w-5" />} tone="slate" />
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.3fr_0.8fr_0.8fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo nhân viên hoặc ca" onChange={setSearch} />
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              {statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
            <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
            <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
          </div>

          {loading ? (
            <LoadingState label="Đang tải yêu cầu đổi ca..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có yêu cầu đổi ca" description="Chưa có dữ liệu phù hợp với bộ lọc hiện tại." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'id', label: 'Mã yêu cầu' },
                  { key: 'employeeName', label: 'Nhân viên gửi' },
                  { key: 'currentShiftCode', label: 'Ca hiện tại', render: (item: SwapRequest) => { const shift = item.currentShiftCode ? getShiftDefinition(item.currentShiftCode) : null; return <span>{shift ? `${shift.name} · ${formatShiftRangeLabel(shift)}` : item.currentShiftName ?? 'Assignment chưa rõ'}</span> } },
                  { key: 'requestedShiftCode', label: 'Ca muốn đổi', render: (item: SwapRequest) => { const shift = item.requestedShiftCode ? getShiftDefinition(item.requestedShiftCode) : null; return <span>{shift ? `${shift.name} · ${formatShiftRangeLabel(shift)}` : item.requestedShiftName ?? 'Nhờ nhận ca'}</span> } },
                  { key: 'requestedDate', label: 'Ngày', render: (item: SwapRequest) => <span>{new Date(item.requestedDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'status', label: 'Trạng thái', render: (item: SwapRequest) => <StatusBadge status={item.status} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: SwapRequest) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelected(item)} aria-label="Xem chi tiết"><Eye className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon" onClick={() => { setManagerNote(''); setDecisionDialog({ id: item.id, type: 'APPROVED' }) }} disabled={item.status !== 'PENDING_APPROVAL'} aria-label="Duyệt yêu cầu"><Check className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon" onClick={() => { setManagerNote(''); setDecisionDialog({ id: item.id, type: 'REJECTED' }) }} disabled={item.status !== 'PENDING_TARGET' && item.status !== 'PENDING_APPROVAL'} aria-label="Từ chối yêu cầu"><X className="h-4 w-4" /></Button>
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
        open={Boolean(decisionDialog)}
        title={decisionDialog?.type === 'APPROVED' ? 'Duyệt yêu cầu đổi ca' : 'Từ chối yêu cầu đổi ca'}
        description={decisionDialog?.type === 'APPROVED' ? 'Bạn xác nhận duyệt yêu cầu đổi ca này.' : apiConfig.useMockApi ? 'Vui lòng nhập lý do từ chối trước khi xác nhận.' : 'Bạn xác nhận từ chối yêu cầu đổi ca này. Ghi chú chỉ hiển thị cục bộ vì backend hiện không nhận lý do.'}
        onCancel={() => setDecisionDialog(null)}
        onConfirm={handleDecision}
      >
        <div className="mt-4">
          <textarea value={managerNote} onChange={(event) => setManagerNote(event.target.value)} rows={3} placeholder={decisionDialog?.type === 'REJECTED' ? 'Nhập lý do từ chối...' : 'Nhập ghi chú duyệt...'} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(selected)}
        title="Chi tiết yêu cầu đổi ca"
        description="Xem thông tin và trạng thái xử lý của yêu cầu công việc này."
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã yêu cầu</span><strong>{selected.id}</strong></div>
            <div className="flex justify-between"><span>Nhân viên</span><strong>{selected.employeeName}</strong></div>
            <div className="flex justify-between"><span>Ca hiện tại</span><strong>{selected.currentShiftCode ? getShiftDefinition(selected.currentShiftCode)?.name ?? selected.currentShiftName ?? 'Assignment chưa rõ' : selected.currentShiftName ?? 'Assignment chưa rõ'}</strong></div>
            <div className="flex justify-between"><span>Ca muốn đổi</span><strong>{selected.requestedShiftCode ? getShiftDefinition(selected.requestedShiftCode)?.name ?? selected.requestedShiftName ?? 'Nhờ nhận ca' : selected.requestedShiftName ?? 'Nhờ nhận ca'}</strong></div>
            <div className="flex justify-between"><span>Ngày gửi</span><strong>{new Date(selected.requestedDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Nhân viên đổi cùng</span><strong>{selected.partnerName ?? 'Không có'}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selected.reason}</strong></div>
            <div className="flex justify-between"><span>Ghi chú xử lý</span><strong>{selected.note ?? selected.managerNote ?? 'Chưa có'}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

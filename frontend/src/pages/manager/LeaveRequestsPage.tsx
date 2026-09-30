import { Check, Clock3, Eye, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { useAppDialog } from '@/components/common/AppDialogContext'
import { DataTable } from '@/components/common/DataTable'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { FormDialog } from '@/components/common/FormDialog'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { SearchInput } from '@/components/common/SearchInput'
import { StatCard } from '@/components/common/StatCard'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { leaveRequestService } from '@/services/leaveRequestService'
import type { LeaveRequest, LeaveRequestDecision, PaginatedResult } from '@/types/management'

const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
]

export function LeaveRequestsPage() {
  const { showDialog } = useAppDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState(() => searchParams.get('status') ?? 'ALL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<LeaveRequest>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [selectedRequest, setSelectedRequest] = useState<LeaveRequest | null>(null)
  const [decisionState, setDecisionState] = useState<LeaveRequestDecision>({ status: 'APPROVED', managerNote: '' })
  const [decisionDialog, setDecisionDialog] = useState<{ id: string; type: 'APPROVED' | 'REJECTED' } | null>(null)

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await leaveRequestService.list({ page, pageSize: 5, search, status, startDate, endDate })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách yêu cầu nghỉ.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const loadLeaveRequests = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await leaveRequestService.list({ page, pageSize: 5, search, status, startDate, endDate })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách yêu cầu nghỉ.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadLeaveRequests()

    return () => {
      mounted = false
    }
  }, [page, search, status, startDate, endDate])

  const stats = useMemo(() => {
    const pending = data.data.filter((item) => item.status === 'PENDING').length
    const approved = data.data.filter((item) => item.status === 'APPROVED').length
    const rejected = data.data.filter((item) => item.status === 'REJECTED').length

    return { pending, approved, rejected }
  }, [data])

  const handleDecision = async () => {
    if (!decisionDialog) {
      return
    }

    if (decisionDialog.type === 'REJECTED' && !decisionState.managerNote.trim()) {
      showDialog({ variant: 'warning', description: 'Vui lòng nhập lý do từ chối.' })
      return
    }

    try {
      await leaveRequestService.updateStatus(decisionDialog.id, {
        status: decisionDialog.type,
        managerNote: decisionState.managerNote,
      })

      setDecisionDialog(null)
      setSelectedRequest(null)
      await fetchData()
    } catch (decisionError) {
      showDialog({ variant: 'error', description: decisionError instanceof Error ? decisionError.message : 'Không thể cập nhật trạng thái.' })
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý yêu cầu ngày nghỉ" description="Duyệt các đơn nghỉ phép và quản lý yếu tố nhân sự." />

      <section className="grid gap-4 md:grid-cols-3">
        <StatCard label="Chờ duyệt" value={stats.pending} icon={<Clock3 className="h-5 w-5" />} tone="amber" />
        <StatCard label="Đã duyệt" value={stats.approved} icon={<Check className="h-5 w-5" />} tone="green" />
        <StatCard label="Đã từ chối" value={stats.rejected} icon={<X className="h-5 w-5" />} tone="red" />
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.7fr_0.7fr_0.7fr]">
            <SearchInput value={search} placeholder="Tìm theo tên nhân viên" onChange={setSearch} />
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none">
              {statusOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
            <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>

          {loading ? (
            <LoadingState label="Đang tải yêu cầu nghỉ..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có yêu cầu nào" description="Không có yêu cầu khớp với bộ lọc hiện tại." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'id', label: 'Mã yêu cầu' },
                  { key: 'employeeName', label: 'Nhân viên' },
                  { key: 'startDate', label: 'Từ ngày', render: (item: LeaveRequest) => <span>{new Date(item.startDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'endDate', label: 'Đến ngày', render: (item: LeaveRequest) => <span>{new Date(item.endDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'totalDays', label: 'Số ngày' },
                  { key: 'status', label: 'Trạng thái', render: (item: LeaveRequest) => <StatusBadge status={item.status} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: LeaveRequest) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelectedRequest(item)} aria-label="Xem chi tiết"> <Eye className="h-4 w-4" /> </Button>
                      <Button variant="ghost" size="icon" onClick={() => { setDecisionState({ status: 'APPROVED', managerNote: '' }); setDecisionDialog({ id: item.id, type: 'APPROVED' }) }} disabled={item.status !== 'PENDING'} aria-label="Duyệt yêu cầu"><Check className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon" onClick={() => { setDecisionState({ status: 'REJECTED', managerNote: '' }); setDecisionDialog({ id: item.id, type: 'REJECTED' }) }} disabled={item.status !== 'PENDING'} aria-label="Từ chối yêu cầu"><X className="h-4 w-4" /></Button>
                    </div>
                  ) },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <FormDialog
        open={Boolean(selectedRequest)}
        title="Chi tiết yêu cầu nghỉ"
        description="Xem và xử lý yêu cầu nghỉ phép của nhân viên."
        onClose={() => setSelectedRequest(null)}
        onSubmit={() => setSelectedRequest(null)}
        submitLabel="Đóng"
      >
        {selectedRequest && (
          <div className="space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã yêu cầu</span><strong>{selectedRequest.id}</strong></div>
            <div className="flex justify-between"><span>Nhân viên</span><strong>{selectedRequest.employeeName}</strong></div>
            <div className="flex justify-between"><span>Ngày bắt đầu</span><strong>{new Date(selectedRequest.startDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Ngày kết thúc</span><strong>{new Date(selectedRequest.endDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Tổng số ngày</span><strong>{selectedRequest.totalDays}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selectedRequest.reason}</strong></div>
            <div className="flex justify-between"><span>Thời gian gửi</span><strong>{new Date(selectedRequest.requestedAt).toLocaleString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selectedRequest.status} /></div>
            {selectedRequest.managerNote && (
              <div className="flex justify-between"><span>Ghi chú quản lý</span><strong>{selectedRequest.managerNote}</strong></div>
            )}
          </div>
        )}
      </FormDialog>

      <ConfirmDialog
        open={Boolean(decisionDialog)}
        title={decisionDialog?.type === 'APPROVED' ? 'Duyệt yêu cầu nghỉ' : 'Từ chối yêu cầu nghỉ'}
        description={decisionDialog?.type === 'APPROVED' ? 'Bạn có chắc chắn muốn duyệt yêu cầu này không?' : 'Vui lòng nhập lý do từ chối trước khi xác nhận.'}
        onCancel={() => setDecisionDialog(null)}
        onConfirm={handleDecision}
      >
        <div className="mt-4 space-y-2">
          <textarea value={decisionState.managerNote} onChange={(event) => setDecisionState((current) => ({ ...current, managerNote: event.target.value }))} rows={3} placeholder={decisionDialog?.type === 'REJECTED' ? 'Nhập lý do từ chối...' : 'Nhập ghi chú duyệt...'} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
        </div>
      </ConfirmDialog>
    </div>
  )
}

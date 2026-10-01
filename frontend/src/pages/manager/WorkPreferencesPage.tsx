import { Check, Filter, Search, X } from 'lucide-react'
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
import { apiConfig } from '@/config/api'
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import { preferenceService } from '@/services/preferenceService'
import type { PaginatedResult, WorkPreference, WorkPreferenceDecision } from '@/types/management'

const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
]

const preferenceTypeLabels: Record<WorkPreference['type'], string> = {
  PREFERRED: 'Muốn làm',
  AVOID: 'Không muốn làm',
}

const formatDateOnly = (value: string) => {
  const [year, month, day] = value.split('-')
  return day && month && year ? `${day}/${month}/${year}` : value
}

export function WorkPreferencesPage() {
  const { showDialog } = useAppDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState(() => searchParams.get('status') ?? 'ALL')
  const [type, setType] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<WorkPreference>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [selectedPreference, setSelectedPreference] = useState<WorkPreference | null>(null)
  const [decisionDialog, setDecisionDialog] = useState<{ id: string; type: 'APPROVED' | 'REJECTED' } | null>(null)
  const [decisionState, setDecisionState] = useState<WorkPreferenceDecision>({ status: 'APPROVED', adminNote: '' })

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await preferenceService.list({ page, pageSize: 5, search, status, type })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách nguyện vọng.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const loadPreferences = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await preferenceService.list({ page, pageSize: 5, search, status, type })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách nguyện vọng.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadPreferences()

    return () => {
      mounted = false
    }
  }, [page, search, status, type])

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

    if (decisionDialog.type === 'REJECTED' && apiConfig.useMockApi && !decisionState.adminNote.trim()) {
      showDialog({ variant: 'warning', description: 'Vui lòng nhập lý do từ chối.' })
      return
    }

    try {
      await preferenceService.updateStatus(decisionDialog.id, {
        status: decisionDialog.type,
        adminNote: decisionState.adminNote,
      })

      setDecisionDialog(null)
      setSelectedPreference(null)
      await fetchData()
    } catch (decisionError) {
      showDialog({ variant: 'error', description: decisionError instanceof Error ? decisionError.message : 'Không thể cập nhật nguyện vọng.' })
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý nguyện vọng làm việc" description="Xem và phê duyệt các nguyện vọng của nhân viên theo loại ca và thời gian." />

      <section className="grid gap-4 md:grid-cols-3">
        <StatCard label="Chờ duyệt" value={stats.pending} icon={<Filter className="h-5 w-5" />} tone="amber" />
        <StatCard label="Đã duyệt" value={stats.approved} icon={<Check className="h-5 w-5" />} tone="green" />
        <StatCard label="Từ chối" value={stats.rejected} icon={<X className="h-5 w-5" />} tone="red" />
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo tên nhân viên hoặc ca" onChange={setSearch} />
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none">
              {statusOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <select value={type} onChange={(event) => { setType(event.target.value); setPage(1) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none">
              <option value="ALL">Tất cả loại</option>
              <option value="PREFERRED">Muốn làm</option>
              <option value="AVOID">Không muốn làm</option>
              <option value="PREFER_SHIFT">Ưu tiên ca</option>
              <option value="AVOID_SHIFT">Tránh ca</option>
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải nguyện vọng..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có nguyện vọng nào" description="Chưa có dữ liệu phù hợp với bộ lọc hiện tại." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'employeeName', label: 'Nhân viên' },
                  { key: 'type', label: 'Loại', render: (item: WorkPreference) => <span>{preferenceTypeLabels[item.type]}</span> },
                  { key: 'relatedShiftCode', label: 'Ca liên quan', render: (item: WorkPreference) => <span>{item.relatedShiftCode ? `${getShiftDefinition(item.relatedShiftCode)?.name ?? item.targetLabel ?? item.relatedShiftCode} (${item.relatedShiftCode})` : (item.targetLabel ?? 'Không có')}</span> },
                  { key: 'preferenceDate', label: 'Ngày nguyện vọng', render: (item: WorkPreference) => <span>{formatDateOnly(item.preferenceDate)}</span> },
                  { key: 'status', label: 'Trạng thái', render: (item: WorkPreference) => <StatusBadge status={item.status} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: WorkPreference) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelectedPreference(item)} aria-label="Xem chi tiết"><Search className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon" onClick={() => { setDecisionState({ status: 'APPROVED', adminNote: '' }); setDecisionDialog({ id: item.id, type: 'APPROVED' }) }} disabled={item.status !== 'PENDING'} aria-label="Duyệt nguyện vọng"><Check className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon" onClick={() => { setDecisionState({ status: 'REJECTED', adminNote: '' }); setDecisionDialog({ id: item.id, type: 'REJECTED' }) }} disabled={item.status !== 'PENDING'} aria-label="Từ chối nguyện vọng"><X className="h-4 w-4" /></Button>
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
        open={Boolean(selectedPreference)}
        title="Chi tiết nguyện vọng"
        description="Xem thông tin nguyện vọng làm việc của nhân viên."
        onClose={() => setSelectedPreference(null)}
        onSubmit={() => setSelectedPreference(null)}
        submitLabel="Đóng"
      >
        {selectedPreference && (
          <div className="space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Nhân viên</span><strong>{selectedPreference.employeeName}</strong></div>
            <div className="flex justify-between"><span>Loại</span><strong>{preferenceTypeLabels[selectedPreference.type]}</strong></div>
            <div className="flex justify-between"><span>Ca liên quan</span><strong>{selectedPreference.relatedShiftCode ? `${getShiftDefinition(selectedPreference.relatedShiftCode)?.name ?? selectedPreference.targetLabel ?? selectedPreference.relatedShiftCode} (${selectedPreference.relatedShiftCode})` : (selectedPreference.targetLabel ?? 'Không có')}</strong></div>
            <div className="flex justify-between"><span>Ngày nguyện vọng</span><strong>{formatDateOnly(selectedPreference.preferenceDate)}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selectedPreference.status} /></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selectedPreference.note || 'Không có'}</strong></div>
            {selectedPreference.adminNote && <div className="flex justify-between"><span>Phản hồi quản lý</span><strong>{selectedPreference.adminNote}</strong></div>}
          </div>
        )}
      </FormDialog>

      <ConfirmDialog
        open={Boolean(decisionDialog)}
        title={decisionDialog?.type === 'APPROVED' ? 'Duyệt nguyện vọng' : 'Từ chối nguyện vọng'}
        description={
          decisionDialog?.type === 'APPROVED'
            ? apiConfig.useMockApi
              ? 'Bạn xác nhận muốn duyệt nguyện vọng này?'
              : 'Bạn xác nhận muốn duyệt nguyện vọng này? Ghi chú chỉ hiển thị cục bộ vì backend hiện không nhận lý do.'
            : apiConfig.useMockApi
              ? 'Bạn xác nhận muốn từ chối nguyện vọng này?'
              : 'Bạn xác nhận muốn từ chối nguyện vọng này? Ghi chú chỉ hiển thị cục bộ vì backend hiện không nhận lý do.'
        }
        onCancel={() => setDecisionDialog(null)}
        onConfirm={handleDecision}
      >
        <div className="mt-4 space-y-2">
          <textarea value={decisionState.adminNote} onChange={(event) => setDecisionState((current) => ({ ...current, adminNote: event.target.value }))} rows={3} placeholder={decisionDialog?.type === 'REJECTED' ? 'Nhập lý do từ chối...' : 'Nhập ghi chú phê duyệt...'} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
        </div>
      </ConfirmDialog>
    </div>
  )
}

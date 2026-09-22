import { CalendarClock, Check, Eye, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
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
import { apiConfig } from '@/config/api'
import { useAuth } from '@/contexts/useAuth'
import { backendAuthService } from '@/services/backendAuthService'
import { swapRequestService } from '@/services/swapRequestService'
import type { CoworkerOption, PaginatedResult, SwapAssignmentOption, SwapRequest } from '@/types/management'

const statusBadgeLabels: Record<string, string> = {
  PENDING_TARGET: 'chờ nhân viên phản hồi',
  PENDING_APPROVAL: 'chờ quản lý duyệt',
  APPROVED: 'đã duyệt',
  REJECTED: 'đã từ chối',
  CANCELLED: 'đã hủy',
}

export function EmployeeSwapRequestsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<SwapRequest | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [data, setData] = useState<PaginatedResult<SwapRequest>>({ data: [], page: 1, pageSize: 50, total: 0, totalPages: 1 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [assignments, setAssignments] = useState<SwapAssignmentOption[]>([])
  const [coworkers, setCoworkers] = useState<CoworkerOption[]>([])
  const [optionsLoading, setOptionsLoading] = useState(false)
  const [optionsError, setOptionsError] = useState('')
  const [form, setForm] = useState({ requesterAssignmentId: '', targetEmployeeId: '', reason: '' })

  const { user } = useAuth()
  const [authoritativeEmployeeId, setAuthoritativeEmployeeId] = useState<string | null>(null)
  const currentEmployeeId = authoritativeEmployeeId ?? user?.id

  useEffect(() => {
    if (apiConfig.useMockApi) {
      return
    }

    let mounted = true
    backendAuthService.me()
      .then((meUser) => {
        if (mounted) {
          setAuthoritativeEmployeeId(meUser.id)
        }
      })
      .catch(() => {
        if (mounted) {
          setAuthoritativeEmployeeId(null)
        }
      })

    return () => {
      mounted = false
    }
  }, [])

  const loadData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await swapRequestService.listMine({ page: 1, pageSize: 50, search: '', status: statusFilter })
      setData(result)
    } catch (loadErr) {
      setError(loadErr instanceof Error ? loadErr.message : 'Không thể tải yêu cầu đổi ca.')
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

    const fetchRequests = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await swapRequestService.listMine({ page: 1, pageSize: 50, search: '', status: statusFilter })
        if (mounted) {
          setData(result)
        }
      } catch (loadErr) {
        if (mounted) {
          setError(loadErr instanceof Error ? loadErr.message : 'Không thể tải yêu cầu đổi ca.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void fetchRequests()

    return () => {
      mounted = false
    }
  }, [statusFilter])

  const filteredRequests = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (!normalizedSearch) {
      return data.data
    }
    return data.data.filter((request) => {
      const candidates = [
        request.reason ?? '',
        request.partnerName ?? '',
        request.employeeName ?? '',
        request.currentShiftName ?? '',
        request.requestedShiftName ?? '',
        request.status.toLowerCase(),
        statusBadgeLabels[request.status] ?? '',
      ]
      return candidates.some((value) => value.toLowerCase().includes(normalizedSearch))
    })
  }, [data.data, search])

  useEffect(() => {
    if (!showForm) {
      return
    }

    let mounted = true

    const loadOptions = async () => {
      setOptionsLoading(true)
      setOptionsError('')

      try {
        const [assignmentOptions, coworkerOptions] = await Promise.all([
          swapRequestService.listMyAssignments(),
          swapRequestService.listCoworkers(),
        ])
        if (mounted) {
          setAssignments(assignmentOptions)
          setCoworkers(coworkerOptions)
        }
      } catch (optionsErr) {
        if (mounted) {
          setOptionsError(optionsErr instanceof Error ? optionsErr.message : 'Không thể tải ca làm và danh sách nhân viên.')
        }
      } finally {
        if (mounted) {
          setOptionsLoading(false)
        }
      }
    }

    void loadOptions()

    return () => {
      mounted = false
    }
  }, [showForm])

  const handleSubmit = async () => {
    if (!form.requesterAssignmentId || !form.targetEmployeeId || !form.reason.trim()) {
      window.alert('Phải chọn ca của bạn, nhân viên muốn đổi cùng và nhập lý do.')
      return
    }

    if (optionsError) {
      window.alert(optionsError)
      return
    }

    const selectedAssignment = assignments.find((assignment) => assignment.id === form.requesterAssignmentId)
    if (selectedAssignment && selectedAssignment.periodStatus !== 'PUBLISHED') {
      window.alert('Chỉ có thể tạo yêu cầu đổi ca khi kỳ công đã được xuất bản (PUBLISHED).')
      return
    }

    try {
      await swapRequestService.createMine({
        requesterAssignmentId: form.requesterAssignmentId,
        targetEmployeeId: form.targetEmployeeId,
        reason: form.reason.trim(),
      })
      setShowForm(false)
      setForm({ requesterAssignmentId: '', targetEmployeeId: '', reason: '' })
      await loadData()
      window.alert('Yêu cầu đổi ca đã được tạo và đang chờ quản lý phê duyệt.')
    } catch (submitError) {
      window.alert(submitError instanceof Error ? submitError.message : 'Không thể tạo yêu cầu đổi ca.')
    }
  }

  const handleCancel = async (id: string) => {
    try {
      await swapRequestService.cancelMine(id)
      await loadData()
    } catch (cancelError) {
      window.alert(cancelError instanceof Error ? cancelError.message : 'Không thể hủy yêu cầu.')
    }
  }

  const handleRespond = async (id: string, accept: boolean) => {
    try {
      await swapRequestService.respondMine(id, accept)
      await loadData()
    } catch (respondError) {
      window.alert(respondError instanceof Error ? respondError.message : 'Không thể phản hồi yêu cầu.')
    }
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
              <option value="PENDING_TARGET">Chờ nhân viên phản hồi</option>
              <option value="PENDING_APPROVAL">Chờ quản lý duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Đã từ chối</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải yêu cầu đổi ca..." />
          ) : error ? (
            <ErrorState message={error} onRetry={loadData} />
          ) : filteredRequests.length === 0 ? (
            <EmptyState title="Không có yêu cầu đổi ca" description="Bạn chưa có yêu cầu nào phù hợp với bộ lọc." />
          ) : (
            <>
              <DataTable
                data={filteredRequests}
                columns={[
                  { key: 'id', label: 'Mã yêu cầu' },
                  { key: 'currentShiftName', label: 'Ca hiện tại', render: (item: SwapRequest) => <span>{item.currentShiftName ?? '—'}</span> },
                  { key: 'requestedShiftName', label: 'Ca muốn đổi', render: (item: SwapRequest) => <span>{item.requestedShiftName ?? '—'}</span> },
                  { key: 'partnerName', label: 'Nhân viên đổi cùng', render: (item: SwapRequest) => <span>{item.partnerName ?? '—'}</span> },
                  { key: 'requestedDate', label: 'Ngày mong muốn' },
                  { key: 'status', label: 'Trạng thái', render: (item: SwapRequest) => <StatusBadge status={item.status} /> },
                  {
                    key: 'actions',
                    label: 'Thao tác',
                    render: (item: SwapRequest) => {
                      const canCancel = item.requesterEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET'
                      const canRespond = item.targetEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET'
                      return (
                        <div className="flex items-center gap-2">
                          <Button variant="ghost" size="icon" onClick={() => setSelected(item)} aria-label="Xem chi tiết"><Eye className="h-4 w-4" /></Button>
                          {canCancel && <Button variant="ghost" size="icon" onClick={() => handleCancel(item.id)} aria-label="Hủy yêu cầu"><X className="h-4 w-4" /></Button>}
                          {canRespond && (
                            <>
                              <Button variant="ghost" size="icon" onClick={() => handleRespond(item.id, true)} aria-label="Đồng ý đổi ca"><Check className="h-4 w-4" /></Button>
                              <Button variant="ghost" size="icon" onClick={() => handleRespond(item.id, false)} aria-label="Từ chối đổi ca"><X className="h-4 w-4" /></Button>
                            </>
                          )}
                        </div>
                      )
                    },
                  },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={() => {}} />
            </>
          )}
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
            <label className="text-sm text-slate-700">Ca của bạn</label>
            <select value={form.requesterAssignmentId} disabled={optionsLoading || Boolean(optionsError)} onChange={(event) => setForm((current) => ({ ...current, requesterAssignmentId: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-50">
              <option value="">{optionsLoading ? 'Đang tải ca làm...' : 'Chọn ca của bạn'}</option>
              {assignments.map((assignment) => (
                <option key={assignment.id} value={assignment.id}>{`${assignment.workDate} • ${assignment.shiftName} ${assignment.shiftStartTime}-${assignment.shiftEndTime} • ${assignment.periodName} (${assignment.periodStatus})`}</option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Nhân viên muốn đổi cùng</label>
            <select value={form.targetEmployeeId} disabled={optionsLoading || Boolean(optionsError)} onChange={(event) => setForm((current) => ({ ...current, targetEmployeeId: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-50">
              <option value="">{optionsLoading ? 'Đang tải nhân viên...' : 'Chọn nhân viên'}</option>
              {coworkers.map((coworker) => (
                <option key={coworker.id} value={coworker.id}>{`${coworker.fullName} (${coworker.employeeCode})`}</option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Lý do</label>
            <textarea value={form.reason} onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <p className="text-xs text-slate-500">Không cần chọn ca của nhân viên đối ứng trong giai đoạn này (targetAssignment không bắt buộc).</p>
          {optionsError && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{optionsError}</div>}
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
            <div className="flex justify-between"><span>Người gửi</span><strong>{selected.employeeName}{selected.requesterEmployeeId ? ` (#${selected.requesterEmployeeId})` : ''}</strong></div>
            <div className="flex justify-between"><span>Nhân viên đổi cùng</span><strong>{selected.partnerName ?? (selected.targetEmployeeId ? `#${selected.targetEmployeeId}` : '—')}</strong></div>
            <div className="flex justify-between"><span>Ca yêu cầu (requesterAssignment)</span><strong>{selected.requesterAssignmentId ?? '—'}</strong></div>
            <div className="flex justify-between"><span>Ca đối ứng (targetAssignment)</span><strong>{selected.targetAssignmentId ?? '—'}</strong></div>
            <div className="flex justify-between"><span>Ngày mong muốn</span><strong>{selected.requestedDate}</strong></div>
            <div className="flex justify-between"><span>Lý do</span><strong>{selected.reason}</strong></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selected.note ?? selected.managerNote ?? 'Chưa có'}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
            {selected.reviewedBy && <div className="flex justify-between"><span>Người duyệt</span><strong>{selected.reviewedBy}</strong></div>}
            {selected.reviewedAt && <div className="flex justify-between"><span>Thời gian duyệt</span><strong>{selected.reviewedAt}</strong></div>}
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

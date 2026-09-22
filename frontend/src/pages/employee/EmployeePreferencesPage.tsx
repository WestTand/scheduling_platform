import { CalendarRange, Eye, PencilLine, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/common/DataTable'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { SearchInput } from '@/components/common/SearchInput'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { getShiftDefinition, shiftDefinitions } from '@/configs/shiftDefinitions'
import { preferenceService } from '@/services/preferenceService'
import type { PreferenceType, WorkPreference } from '@/types/management'

const preferenceLabels: Record<string, string> = {
  PREFERRED: 'Muốn làm',
  AVOID: 'Không muốn làm',
  PREFER_SHIFT: 'Ưu tiên ca',
  AVOID_SHIFT: 'Tránh ca',
}

// NOTE: frontend shiftDefinitions carry no numeric id, and the only backend
// shift-type list (GET /api/admin/shift-types) requires MANAGER/ADMIN, so it is
// not usable from this EMPLOYEE page (would 403). Resolve therefore stays null
// and relatedShiftCode is kept locally for display; backend creates send
// shiftTypeId only (null) without silent loss of the local code.
const resolveShiftTypeId = (code: string): number | null => {
  const found = shiftDefinitions.find((shift) => shift.code === code) as unknown as { id?: unknown } | undefined
  if (!found || found.id === undefined || found.id === null || found.id === '') {
    return null
  }
  const numeric = Number(found.id)
  return Number.isNaN(numeric) ? null : numeric
}

export function EmployeePreferencesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [showForm, setShowForm] = useState(() => searchParams.get('action') === 'create')
  const [selected, setSelected] = useState<WorkPreference | null>(null)
  const [search, setSearch] = useState('')
  const [monthFilter, setMonthFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState(() => searchParams.get('status') ?? 'ALL')
  const [preferences, setPreferences] = useState<WorkPreference[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<WorkPreference | null>(null)
  const [form, setForm] = useState({ month: '2026-10', type: 'PREFERRED', relatedShiftCode: 'THONG_TAM', priority: '1', note: '', })

  const loadData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await preferenceService.listMine({ page: 1, pageSize: 50, status: statusFilter })
      setPreferences(result.data)
    } catch (loadErr) {
      setError(loadErr instanceof Error ? loadErr.message : 'Không thể tải nguyện vọng.')
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

    const fetchPreferences = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await preferenceService.listMine({ page: 1, pageSize: 50, status: statusFilter })
        if (mounted) {
          setPreferences(result.data)
        }
      } catch (loadErr) {
        if (mounted) {
          setError(loadErr instanceof Error ? loadErr.message : 'Không thể tải nguyện vọng.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void fetchPreferences()

    return () => {
      mounted = false
    }
  }, [statusFilter])

  const filtered = useMemo(() => preferences.filter((item) => {
    const normalizedSearch = search.trim().toLowerCase()
    const matchesSearch =
      !normalizedSearch ||
      [item.note ?? '', item.relatedShiftCode ?? '', item.targetLabel ?? '', item.employeeName ?? ''].some((value) =>
        value.toLowerCase().includes(normalizedSearch),
      )
    const matchesMonth = monthFilter === 'ALL' || item.month === monthFilter
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
    return matchesSearch && matchesMonth && matchesStatus
  }), [preferences, search, monthFilter, statusFilter])

  const handleEdit = (item: WorkPreference) => {
    setEditing(item)
    setForm({
      month: item.month,
      type: item.type,
      relatedShiftCode: item.relatedShiftCode ?? 'THONG_TAM',
      priority: String(item.priority),
      note: item.note ?? '',
    })
    setShowForm(true)
  }

  const handleSubmit = async () => {
    if (!form.note.trim()) {
      window.alert('Nội dung ghi chú không được bỏ trống.')
      return
    }

    const preferenceDate = form.month.length === 7 ? `${form.month}-01` : form.month

    try {
      const input = {
        preferenceDate,
        type: form.type as PreferenceType,
        shiftTypeId: resolveShiftTypeId(form.relatedShiftCode),
        // Kept for mock-mode display (relatedShiftCode/targetLabel); backend branch forwards it as shiftCode.
        relatedShiftCode: form.relatedShiftCode,
        priority: Number(form.priority),
        note: form.note.trim(),
      }

      if (editing) {
        await preferenceService.updateMine(editing.id, input)
      } else {
        await preferenceService.createMine(input)
      }

      setShowForm(false)
      setEditing(null)
      setForm({ month: '2026-10', type: 'PREFERRED', relatedShiftCode: 'THONG_TAM', priority: '1', note: '' })
      await loadData()
      window.alert('Nguyện vọng đã được gửi và đang chờ xem xét.')
    } catch (submitError) {
      window.alert(submitError instanceof Error ? submitError.message : 'Không thể gửi nguyện vọng.')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await preferenceService.cancelMine(id)
      await loadData()
    } catch (deleteError) {
      window.alert(deleteError instanceof Error ? deleteError.message : 'Không thể hủy nguyện vọng.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Đăng ký nguyện vọng" description="Gửi và quản lý nguyện vọng làm việc theo tháng." action={<Button onClick={() => { setEditing(null); setShowForm(true) }}><CalendarRange className="mr-2 h-4 w-4" />Đăng ký nguyện vọng</Button>} />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.2fr_0.8fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo ca hoặc ghi chú" onChange={setSearch} />
            <select value={monthFilter} onChange={(event) => setMonthFilter(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả tháng</option>
              <option value="2026-09">09/2026</option>
              <option value="2026-10">10/2026</option>
            </select>
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); const params = new URLSearchParams(searchParams); params.set('status', event.target.value); setSearchParams(params) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ xét duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
              <option value="REJECTED">Từ chối</option>
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải nguyện vọng..." />
          ) : error ? (
            <ErrorState message={error} onRetry={loadData} />
          ) : filtered.length === 0 ? (
            <EmptyState title="Không có nguyện vọng" description="Bạn chưa có nguyện vọng nào phù hợp với bộ lọc." />
          ) : (
            <DataTable
              data={filtered}
              columns={[
                { key: 'month', label: 'Tháng', render: (item: WorkPreference) => <span>{item.month}</span> },
                { key: 'type', label: 'Loại nguyện vọng', render: (item: WorkPreference) => <span>{preferenceLabels[item.type] ?? item.type}</span> },
                { key: 'relatedShiftCode', label: 'Ca liên quan', render: (item: WorkPreference) => {
                  const definition = item.relatedShiftCode ? getShiftDefinition(item.relatedShiftCode) : undefined
                  if (definition) {
                    return <span>{definition.name} ({item.relatedShiftCode})</span>
                  }
                  return <span>{item.targetLabel ?? '—'}</span>
                } },
                { key: 'priority', label: 'Mức ưu tiên' },
                { key: 'status', label: 'Trạng thái', render: (item: WorkPreference) => <StatusBadge status={item.status} /> },
                { key: 'actions', label: 'Thao tác', render: (item: WorkPreference) => (
                  <div className="flex items-center gap-2">
                    <Button variant="ghost" size="icon" onClick={() => setSelected(item)}><Eye className="h-4 w-4" /></Button>
                    {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => handleEdit(item)}><PencilLine className="h-4 w-4" /></Button>}
                    {item.status === 'PENDING' && <Button variant="ghost" size="icon" onClick={() => handleDelete(item.id)}><Trash2 className="h-4 w-4" /></Button>}
                  </div>
                ) },
              ]}
            />
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={showForm}
        title="Đăng ký nguyện vọng"
        description="Nguyện vọng sẽ được hệ thống xem xét khi tạo lịch nhưng không bảo đảm được đáp ứng hoàn toàn."
        onCancel={() => { setShowForm(false); setEditing(null) }}
        onConfirm={handleSubmit}
        confirmLabel="Gửi nguyện vọng"
      >
        <div className="mt-4 grid gap-4">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Khoảng thời gian áp dụng</label>
            <input type="month" value={form.month} onChange={(event) => setForm((current) => ({ ...current, month: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại nguyện vọng</label>
            <select value={form.type} onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="PREFERRED">Muốn làm</option>
              <option value="AVOID">Không muốn làm</option>
              <option value="PREFER_SHIFT">Ưu tiên ca</option>
              <option value="AVOID_SHIFT">Tránh ca</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại ca liên quan</label>
            <select value={form.relatedShiftCode} onChange={(event) => setForm((current) => ({ ...current, relatedShiftCode: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              {shiftDefinitions.map((shift) => <option key={shift.code} value={shift.code}>{shift.name}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Mức ưu tiên</label>
            <input type="number" min={1} max={5} value={form.priority} onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Nội dung ghi chú</label>
            <textarea value={form.note} onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
          </div>
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(selected)}
        title="Chi tiết nguyện vọng"
        description="Thông tin chi tiết về nguyện vọng của bạn."
        onCancel={() => setSelected(null)}
        onConfirm={() => setSelected(null)}
        confirmLabel="Đóng"
      >
        {selected && (
          <div className="mt-4 space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Tháng</span><strong>{selected.month}</strong></div>
            <div className="flex justify-between"><span>Loại</span><strong>{preferenceLabels[selected.type] ?? selected.type}</strong></div>
            <div className="flex justify-between"><span>Ca liên quan</span><strong>{selected.relatedShiftCode ? `${getShiftDefinition(selected.relatedShiftCode)?.name ?? selected.relatedShiftCode} (${selected.relatedShiftCode})` : (selected.targetLabel ?? '—')}</strong></div>
            <div className="flex justify-between"><span>Mức ưu tiên</span><strong>{selected.priority}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selected.status} /></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selected.note}</strong></div>
          </div>
        )}
      </ConfirmDialog>
    </div>
  )
}

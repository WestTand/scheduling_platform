import { useMemo, useState } from 'react'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { backendEmployeePoolService } from '@/services/backendEmployeePoolService'
import { buildEmployeePoolPayload, getEmployeePoolSelection } from './ScheduleEmployeePoolEditor.helpers'
import type { PoolCandidate } from './ScheduleEmployeePoolEditor.helpers'

interface ScheduleEmployeePoolEditorProps {
  open: boolean
  periodId: string
  candidates: PoolCandidate[]
  currentPoolIds: string[]
  onCancel: () => void
  onSaved: (selectedIds: number[]) => void
}

export function ScheduleEmployeePoolEditor({ open, periodId, candidates, currentPoolIds, onCancel, onSaved }: ScheduleEmployeePoolEditorProps) {
  const [selectedIds, setSelectedIds] = useState<string[] | null>(null)
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  const selection = useMemo(() => selectedIds ?? getEmployeePoolSelection(candidates, currentPoolIds), [candidates, currentPoolIds, selectedIds])
  const visibleCandidates = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return candidates
    return candidates.filter((candidate) => [candidate.fullName, candidate.employeeCode].some((value) => value.toLowerCase().includes(keyword)))
  }, [candidates, search])
  const allVisibleSelected = visibleCandidates.length > 0 && visibleCandidates.every((candidate) => selection.includes(candidate.id))

  const toggleCandidate = (id: string) => {
    setSelectedIds(selection.includes(id) ? selection.filter((item) => item !== id) : [...selection, id])
  }

  const toggleAll = () => {
    setSelectedIds(allVisibleSelected ? selection.filter((id) => !visibleCandidates.some((candidate) => candidate.id === id)) : [...new Set([...selection, ...visibleCandidates.map((candidate) => candidate.id)])])
  }

  const handleConfirm = async () => {
    if (!selection.length) {
      setError('Vui lòng chọn ít nhất một nhân viên cho pool.')
      return
    }

    setError('')
    setIsSaving(true)
    try {
      const employeeIds = selection.map(Number)
      await backendEmployeePoolService.update(periodId, employeeIds)
      setSelectedIds(null)
      setSearch('')
      onSaved(employeeIds)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Không thể cập nhật pool nhân viên.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <ConfirmDialog
      open={open}
      title="Chỉnh sửa pool nhân viên"
      description="Pool nhân viên là tập người mà AI được phép xếp ca. Thêm người vào pool nếu lịch báo thiếu nhân lực."
      cancelLabel="Hủy"
      confirmLabel={isSaving ? 'Đang lưu...' : 'Lưu pool'}
      onCancel={onCancel}
      onConfirm={() => void handleConfirm()}
    >
      <div className="mt-4 space-y-3">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo tên hoặc mã nhân viên"
          className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
        />
        <div className="flex items-center justify-between text-sm text-slate-600">
          <span>Đã chọn {selection.length} nhân viên</span>
          <Button type="button" variant="outline" size="sm" onClick={toggleAll}>{allVisibleSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}</Button>
        </div>
        <div className="max-h-60 space-y-2 overflow-y-auto rounded-xl border border-slate-200 p-3">
          {visibleCandidates.map((candidate) => (
            <label key={candidate.id} className="flex items-center gap-3 rounded-xl border border-slate-200 p-3 text-sm text-slate-700">
              <input type="checkbox" checked={selection.includes(candidate.id)} onChange={() => toggleCandidate(candidate.id)} />
              <span className="min-w-0 flex-1 truncate">{candidate.fullName}</span>
              <span className="text-xs text-slate-400">{candidate.employeeCode}</span>
            </label>
          ))}
          {!visibleCandidates.length && <p className="px-1 py-2 text-sm text-slate-500">Không có nhân viên phù hợp.</p>}
        </div>
        {buildEmployeePoolPayload(selection) === '' && <p className="text-sm text-slate-500">Pool phải có ít nhất một nhân viên.</p>}
        {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      </div>
    </ConfirmDialog>
  )
}
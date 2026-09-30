import { AlertTriangle, ArrowLeft, CalendarDays, Save, Send, Sparkles, Trash2, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { formatShiftRangeLabel, getShiftDefinition, getShiftDefinitionsForDate } from '@/configs/shiftDefinitions'
import { apiConfig } from '@/config/api'
import { scheduleDetailSeed, scheduleStatusLabels } from '@/mocks/schedules'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import { backendScheduleService } from '@/services/backendScheduleService'
import { canRunScheduleEditOptimize, canSubmitEditRequest, filterScheduleEditAssignments, getScheduleEditDeleteDisabled, getScheduleEditDialogDescription, getScheduleEditLockedMessage, getScheduleEditModeLabel, getScheduleEditNoticeClass, getScheduleEditRowIntent, reloadScheduleEditDetail, shouldShowMockStatusActions, type ScheduleEditAssignmentFilter } from './ScheduleEditPage.helpers'
import type { ShiftCode } from '@/configs/shiftDefinitions'
import type { Employee } from '@/types/management'
import type { ScheduleAssignment, ScheduleDetail } from '@/types/schedule'
import type { ScheduleEditNotice } from './ScheduleEditPage.helpers'

const emptyAssignmentForm = {
  date: '2026-09-17',
  shiftCode: 'THONG_TAM' as ShiftCode,
  employeeName: 'Trần Văn Nam',
  note: '',
}

const backendShiftNameByCode: Record<ShiftCode, string> = {
  THONG_TAM: 'thong tam',
  NGOAI_GIO_1: 'ngoai gio 1',
  NGOAI_GIO_2: 'ngoai gio 2',
  TRUC_SANG: 'truc sang',
  TRUC_CHIEU: 'truc chieu',
  TRUC_DEM: 'truc dem',
}

export function ScheduleEditPage() {
  const { scheduleId } = useParams()
  const navigate = useNavigate()
  const [assignments, setAssignments] = useState<ScheduleAssignment[]>(scheduleDetailSeed.assignments)
  const [formState, setFormState] = useState(emptyAssignmentForm)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [backendResult, setBackendResult] = useState<{ scheduleId: string; detail: ScheduleDetail | null; error: string } | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [targetWorkShiftId, setTargetWorkShiftId] = useState<string>('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [notice, setNotice] = useState<ScheduleEditNotice | null>(null)
  const [assignmentFilter, setAssignmentFilter] = useState<ScheduleEditAssignmentFilter>('ALL')
  const [isOptimizing, setIsOptimizing] = useState(false)

  const mockDetail = scheduleId === scheduleDetailSeed.id ? scheduleDetailSeed : null
  const detail = apiConfig.useMockApi ? mockDetail : backendResult && scheduleId === backendResult.scheduleId ? backendResult.detail : null

  useEffect(() => {
    if (apiConfig.useMockApi || !scheduleId) return
    let ignore = false
    Promise.all([
      backendScheduleService.getScheduleDetail(scheduleId),
      backendEmployeeService.list({ page: 1, pageSize: 100, status: 'ACTIVE' }),
    ]).then(([schedule, employeePage]) => {
      if (ignore) return
      setBackendResult({ scheduleId, detail: schedule, error: '' })
      setAssignments(schedule.assignments)
      setEmployees(employeePage.data)
      const firstEmployee = employeePage.data[0]
      if (firstEmployee) setFormState((current) => ({ ...current, employeeName: firstEmployee.fullName }))
    }).catch((loadError) => {
      if (!ignore) {
        const message = loadError instanceof Error ? loadError.message : 'Không thể tải dữ liệu lịch.'
        setBackendResult({ scheduleId, detail: null, error: message })
        setNotice({ type: 'error', message })
      }
    })
    return () => { ignore = true }
  }, [scheduleId])

  const employeeOptions = useMemo(() => apiConfig.useMockApi ? ['Trần Văn Nam', 'Lê Thị Hòa', 'Hoàng Văn Tâm', 'Phạm Minh Khoa'] : employees.map((employee) => employee.fullName), [employees])
  const shiftOptions = useMemo(() => getShiftDefinitionsForDate(formState.date).map((shift) => ({ value: shift.code, label: `${shift.name} • ${formatShiftRangeLabel(shift)}` })), [formState.date])

  const findEmployeeId = (employeeName: string) => employees.find((employee) => employee.fullName === employeeName)?.id
  const showSuccess = (message: string) => setNotice({ type: 'success', message })
  const showError = (message: string) => setNotice({ type: 'error', message })

  const refreshScheduleDetail = () => {
    if (!scheduleId) return Promise.resolve(null)
    return reloadScheduleEditDetail({
      scheduleId,
      loadDetail: backendScheduleService.getScheduleDetail,
      setBackendResult,
      setAssignments,
    })
  }

  const isLoading = !apiConfig.useMockApi && Boolean(scheduleId) && (!backendResult || backendResult.scheduleId !== scheduleId)
  const lockedMessage = getScheduleEditLockedMessage(apiConfig.useMockApi, detail?.status)
  const isEditingLocked = Boolean(lockedMessage)
  const filteredAssignments = filterScheduleEditAssignments(assignments, assignmentFilter)

  if (isLoading) {
    return <div className="rounded-xl border border-slate-200 bg-white px-4 py-8 text-center text-sm text-slate-600">Đang tải dữ liệu chỉnh sửa lịch...</div>
  }

  if (!detail) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" asChild>
          <Link to="/manager/schedules"><ArrowLeft className="mr-2 h-4 w-4" />Quay lại lịch</Link>
        </Button>
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardContent className="p-6 text-sm text-slate-600">{apiConfig.useMockApi ? 'Không tìm thấy lịch mẫu để chỉnh sửa.' : backendResult?.error || 'Không tìm thấy lịch để chỉnh sửa.'}</CardContent>
        </Card>
      </div>
    )
  }

  const selectedShift = getShiftDefinition(formState.shiftCode)

  const openCreate = () => {
    setEditingId(null)
    setTargetWorkShiftId('')
    setFormState({ ...emptyAssignmentForm, employeeName: apiConfig.useMockApi ? emptyAssignmentForm.employeeName : employees[0]?.fullName ?? '' })
    setDialogOpen(true)
  }

  const openEdit = (assignment: ScheduleAssignment) => {
    const intent = getScheduleEditRowIntent(assignment)
    setEditingId(intent.assignmentId)
    setTargetWorkShiftId(intent.workShiftId)
    setFormState({
      date: assignment.date,
      shiftCode: assignment.shiftCode,
      employeeName: assignment.employeeNames[0] ?? (apiConfig.useMockApi ? emptyAssignmentForm.employeeName : employees[0]?.fullName ?? ''),
      note: assignment.warningCount > 0 ? 'Có cảnh báo mẫu' : '',
    })
    setDialogOpen(true)
  }

  const handleSave = async () => {
    if (!formState.date || !formState.employeeName || !formState.shiftCode) {
      showError('Vui lòng điền đầy đủ thông tin phân công.')
      return
    }

    if (editingId) {
      if (!apiConfig.useMockApi) {
        const employeeId = findEmployeeId(formState.employeeName)
        if (!employeeId) {
          showError('Không tìm thấy nhân viên backend tương ứng.')
          return
        }
        try {
          await backendScheduleService.updateAssignment(editingId, employeeId)
          await refreshScheduleDetail()
          showSuccess('Đã cập nhật phân công trên backend.')
        } catch (saveError) {
          showError(saveError instanceof Error ? saveError.message : 'Không thể cập nhật phân công.')
          return
        }
        setDialogOpen(false)
        setEditingId(null)
        setTargetWorkShiftId('')
        setFormState(emptyAssignmentForm)
        return
      }
      setAssignments((current) => current.map((assignment) => assignment.id === editingId ? {
        ...assignment,
        date: formState.date,
        shiftCode: formState.shiftCode,
        employeeNames: [formState.employeeName],
        warningCount: assignment.warningCount,
      } : assignment))
      showSuccess('Đã cập nhật phân công mẫu trong mock state.')
    } else {
      if (!apiConfig.useMockApi) {
        try {
          const employeeId = findEmployeeId(formState.employeeName)
          const workShiftId = targetWorkShiftId || (await backendScheduleService.listWorkShifts(detail.id).then((items) => items.find((item) => item.workDate === formState.date && item.shiftName.trim().toLowerCase() === backendShiftNameByCode[formState.shiftCode])?.id))
          if (!employeeId || !workShiftId) {
            showError('Không tìm thấy nhân viên hoặc ca làm việc backend tương ứng.')
            return
          }
          await backendScheduleService.createAssignment(String(workShiftId), employeeId)
          await refreshScheduleDetail()
          showSuccess('Đã thêm phân công trên backend.')
        } catch (saveError) {
          showError(saveError instanceof Error ? saveError.message : 'Không thể thêm phân công.')
          return
        }
        setDialogOpen(false)
        setEditingId(null)
        setTargetWorkShiftId('')
        setFormState(emptyAssignmentForm)
        return
      }
      const nextAssignment: ScheduleAssignment = {
        id: `mock-pop-${Date.now()}`,
        scheduleId: detail.id,
        date: formState.date,
        shiftCode: formState.shiftCode,
        employeeNames: [formState.employeeName],
        warningCount: formState.note ? 1 : 0,
      }

      setAssignments((current) => [nextAssignment, ...current])
      showSuccess('Đã thêm phân công mẫu vào lịch đang chỉnh sửa.')
    }

    setDialogOpen(false)
    setEditingId(null)
    setFormState(emptyAssignmentForm)
  }

  const handleDelete = async () => {
    if (!deleteId) {
      return
    }

    if (!apiConfig.useMockApi) {
      try {
        await backendScheduleService.deleteAssignment(deleteId)
        await refreshScheduleDetail()
        showSuccess('Đã xóa phân công trên backend.')
      } catch (deleteError) {
        showError(deleteError instanceof Error ? deleteError.message : 'Không thể xóa phân công.')
        return
      }
    } else {
      showSuccess('Đã xóa phân công mẫu khỏi lịch nháp.')
      setAssignments((current) => current.filter((assignment) => assignment.id !== deleteId))
    }
    setDeleteId(null)
  }

  const handleSubmitStatus = (status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PUBLISHED') => {
    showSuccess(`Mock state: chuyển lịch sang trạng thái ${scheduleStatusLabels[status] ?? status}.`)
  }

  const handleOptimize = async () => {
    if (!canRunScheduleEditOptimize({ useMockApi: apiConfig.useMockApi, scheduleId: scheduleId ?? '', isEditingLocked, isOptimizing })) return
    setIsOptimizing(true)
    try {
      const result = await backendScheduleService.optimize(scheduleId!)
      await refreshScheduleDetail()
      showSuccess(`Đã tối ưu lại lịch. AI: ${result.aiStatus}${result.solverStatus ? ` · Solver: ${result.solverStatus}` : ''}.`)
    } catch (optimizeError) {
      showError(optimizeError instanceof Error ? optimizeError.message : 'Không thể tối ưu lại lịch.')
    } finally {
      setIsOptimizing(false)
    }
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" asChild>
        <Link to={`/manager/schedules/${detail.id}`}><ArrowLeft className="mr-2 h-4 w-4" />Quay lại chi tiết lịch</Link>
      </Button>

      <PageHeader
        title={`Chỉnh sửa lịch • ${detail.name}`}
        description={`${detail.startDate} - ${detail.endDate} · Trạng thái hiện tại: ${scheduleStatusLabels[detail.status]}`}
      />

      {notice && <div className={getScheduleEditNoticeClass(notice.type)}>{notice.message}</div>}
      {lockedMessage && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{lockedMessage}</div>}

      <section className="grid gap-4 md:grid-cols-4">
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Thời gian</p><p className="mt-2 font-semibold">{detail.startDate} → {detail.endDate}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Trạng thái</p><p className="mt-2 font-semibold">{scheduleStatusLabels[detail.status]}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Phân công</p><p className="mt-2 font-semibold">{assignments.length}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Cảnh báo</p><p className="mt-2 font-semibold">{detail.warnings.length}</p></CardContent></Card>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-wrap gap-3">
            <Button onClick={openCreate} disabled={isEditingLocked}><CalendarDays className="mr-2 h-4 w-4" />Thêm phân công</Button>
            {!apiConfig.useMockApi && <Button variant="outline" onClick={() => void handleOptimize()} disabled={!canRunScheduleEditOptimize({ useMockApi: apiConfig.useMockApi, scheduleId: scheduleId ?? '', isEditingLocked, isOptimizing })}><Sparkles className="mr-2 h-4 w-4" />{isOptimizing ? 'Đang tối ưu...' : 'Tối ưu lại'}</Button>}
            {shouldShowMockStatusActions(apiConfig.useMockApi) && <Button variant="outline" onClick={() => handleSubmitStatus('DRAFT')}><Save className="mr-2 h-4 w-4" />Lưu bản nháp</Button>}
            {shouldShowMockStatusActions(apiConfig.useMockApi) && <Button variant="outline" onClick={() => handleSubmitStatus('PENDING_APPROVAL')}><Send className="mr-2 h-4 w-4" />Gửi duyệt</Button>}
          </div>

          <div className="flex flex-wrap gap-2">
            {[
              { value: 'ALL', label: `Tất cả (${assignments.length})` },
              { value: 'UNASSIGNED', label: `Chưa phân công (${assignments.filter((assignment) => assignment.employeeNames.length === 0).length})` },
              { value: 'WARNING', label: `Có cảnh báo (${assignments.filter((assignment) => assignment.warningCount > 0).length})` },
            ].map((option) => (
              <Button
                key={option.value}
                type="button"
                variant={assignmentFilter === option.value ? 'default' : 'outline'}
                size="sm"
                onClick={() => setAssignmentFilter(option.value as ScheduleEditAssignmentFilter)}
              >
                {option.label}
              </Button>
            ))}
          </div>

          <div className="grid gap-3 md:grid-cols-[1fr_1fr_1fr_1fr]">
            <select className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" defaultValue="">
              <option value="">Tất cả nhân viên</option>
              {employeeOptions.map((employee) => <option key={employee} value={employee}>{employee}</option>)}
            </select>
            <select className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" defaultValue="">
              <option value="">Tất cả loại ca</option>
              {shiftOptions.map((shift) => <option key={shift.value} value={shift.value}>{shift.label.split('•')[0].trim()}</option>)}
            </select>
            <input type="date" defaultValue="2026-09-17" className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-500">{getScheduleEditModeLabel(apiConfig.useMockApi)}</div>
          </div>

          <div className="space-y-3">
            {filteredAssignments.map((assignment) => (
              <div key={assignment.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="font-medium text-slate-900">{getShiftDefinition(assignment.shiftCode)?.name} • {assignment.date}</p>
                  <p className="text-sm text-slate-500">{getShiftDefinition(assignment.shiftCode) && formatShiftRangeLabel(getShiftDefinition(assignment.shiftCode)!)} • {assignment.employeeNames.length ? assignment.employeeNames.join(', ') : 'Chưa phân công'}</p>
                  {assignment.warningCount > 0 && <p className="mt-1 inline-flex items-center gap-2 text-xs text-amber-700"><AlertTriangle className="h-3.5 w-3.5" />Có cảnh báo cần xử lý</p>}
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => openEdit(assignment)} disabled={isEditingLocked}>
                    {getScheduleEditRowIntent(assignment).mode === 'assign' ? 'Gán nhân viên' : 'Sửa'}
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setDeleteId(assignment.id)} disabled={isEditingLocked || getScheduleEditDeleteDisabled(assignment)} className="text-red-600"> <Trash2 className="mr-2 h-4 w-4" />Xóa</Button>
                </div>
              </div>
            ))}
            {filteredAssignments.length === 0 && <div className="rounded-xl border border-dashed border-slate-200 bg-white p-4 text-sm text-slate-500">Không có phân công phù hợp bộ lọc này.</div>}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={Boolean(deleteId)}
        title="Xóa phân công"
        description="Bạn có chắc chắn muốn xóa phân công này khỏi lịch mẫu?"
        onCancel={() => setDeleteId(null)}
        onConfirm={handleDelete}
      />

      <ConfirmDialog
        open={dialogOpen}
        title={editingId ? 'Sửa phân công' : 'Thêm phân công'}
        description={getScheduleEditDialogDescription(apiConfig.useMockApi, !editingId)}
        onCancel={() => setDialogOpen(false)}
        onConfirm={handleSave}
        confirmLabel="Lưu"
      >
        <div className="mt-4 grid gap-4">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ngày làm việc</label>
            <input type="date" value={formState.date} onChange={(event) => { const date = event.target.value; const availableShifts = getShiftDefinitionsForDate(date); setFormState((current) => ({ ...current, date, shiftCode: availableShifts.some((shift) => shift.code === current.shiftCode) ? current.shiftCode : availableShifts[0].code })) }} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại ca</label>
            <select value={formState.shiftCode} onChange={(event) => setFormState((current) => ({ ...current, shiftCode: event.target.value as ShiftCode }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
              {shiftOptions.map((shift) => <option key={shift.value} value={shift.value}>{shift.label}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Nhân viên</label>
            <select value={formState.employeeName} onChange={(event) => setFormState((current) => ({ ...current, employeeName: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
              {employeeOptions.map((employee) => <option key={employee} value={employee}>{employee}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Giờ bắt đầu / kết thúc</label>
            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-600">
              {selectedShift && formatShiftRangeLabel(selectedShift)}
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ghi chú</label>
            <textarea value={formState.note} onChange={(event) => setFormState((current) => ({ ...current, note: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
        </div>
      </ConfirmDialog>

      <div className="flex items-center justify-end gap-3">
        <Button variant="outline" onClick={() => navigate(`/manager/schedules/${detail.id}`)}>Hủy thay đổi</Button>
        {canSubmitEditRequest(apiConfig.useMockApi) && <Button onClick={() => handleSubmitStatus('PUBLISHED')}><UserRound className="mr-2 h-4 w-4" />Gửi duyệt</Button>}
      </div>
    </div>
  )
}

import { AlertTriangle, ArrowLeft, CalendarDays, Save, Send, Trash2, UserRound } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { scheduleDetailSeed, scheduleStatusLabels } from '@/mocks/schedules'
import type { ScheduleAssignment } from '@/types/schedule'

const emptyAssignmentForm = {
  date: '2026-09-17',
  shiftCode: 'DAY',
  employeeName: 'Trần Văn Nam',
  note: '',
}

export function ScheduleEditPage() {
  const { scheduleId } = useParams()
  const navigate = useNavigate()
  const [assignments, setAssignments] = useState<ScheduleAssignment[]>(scheduleDetailSeed.assignments)
  const [formState, setFormState] = useState(emptyAssignmentForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [notice, setNotice] = useState('')

  const detail = scheduleId === scheduleDetailSeed.id ? scheduleDetailSeed : null

  const employeeOptions = useMemo(() => ['Trần Văn Nam', 'Lê Thị Hòa', 'Hoàng Văn Tâm', 'Phạm Minh Khoa'], [])
  const shiftOptions = useMemo(() => [
    { value: 'DAY', label: 'Ca ngày • 07:00 - 12:00' },
    { value: 'EVENING', label: 'Ca chiều • 13:00 - 18:00' },
    { value: 'NIGHT', label: 'Ca đêm • 19:00 - 23:00' },
  ], [])

  if (!detail) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" asChild>
          <Link to="/manager/schedules"><ArrowLeft className="mr-2 h-4 w-4" />Quay lại lịch</Link>
        </Button>
        <Card className="border-slate-200 bg-white shadow-sm">
          <CardContent className="p-6 text-sm text-slate-600">Không tìm thấy lịch mẫu để chỉnh sửa.</CardContent>
        </Card>
      </div>
    )
  }

  const openCreate = () => {
    setEditingId(null)
    setFormState(emptyAssignmentForm)
    setDialogOpen(true)
  }

  const openEdit = (assignment: ScheduleAssignment) => {
    setEditingId(assignment.id)
    setFormState({
      date: assignment.date,
      shiftCode: assignment.shiftCode,
      employeeName: assignment.employeeNames[0] ?? 'Trần Văn Nam',
      note: assignment.warningCount > 0 ? 'Có cảnh báo mẫu' : '',
    })
    setDialogOpen(true)
  }

  const handleSave = () => {
    const selectedShift = shiftOptions.find((shift) => shift.value === formState.shiftCode)
    const startTime = selectedShift?.label.split('•')[1]?.trim().split(' - ')[0] ?? '07:00'
    const endTime = selectedShift?.label.split('•')[1]?.trim().split(' - ')[1] ?? '12:00'

    if (!formState.date || !formState.employeeName || !formState.shiftCode) {
      setNotice('Vui lòng điền đầy đủ thông tin phân công.')
      return
    }

    if (editingId) {
      setAssignments((current) => current.map((assignment) => assignment.id === editingId ? {
        ...assignment,
        date: formState.date,
        shiftCode: formState.shiftCode,
        shiftName: selectedShift?.label.split('•')[0].trim() ?? assignment.shiftName,
        startTime,
        endTime,
        employeeNames: [formState.employeeName],
        warningCount: assignment.warningCount,
      } : assignment))
      setNotice('Đã cập nhật phân công mẫu trong mock state.')
    } else {
      const nextAssignment: ScheduleAssignment = {
        id: `mock-pop-${Date.now()}`,
        scheduleId: detail.id,
        date: formState.date,
        shiftCode: formState.shiftCode,
        shiftName: selectedShift?.label.split('•')[0].trim() ?? 'Ca ngày',
        startTime,
        endTime,
        color: formState.shiftCode === 'EVENING' ? '#f59e0b' : formState.shiftCode === 'NIGHT' ? '#8b5cf6' : '#2563eb',
        employeeNames: [formState.employeeName],
        warningCount: formState.note ? 1 : 0,
      }

      setAssignments((current) => [nextAssignment, ...current])
      setNotice('Đã thêm phân công mẫu vào lịch đang chỉnh sửa.')
    }

    setDialogOpen(false)
    setEditingId(null)
    setFormState(emptyAssignmentForm)
  }

  const handleDelete = () => {
    if (!deleteId) {
      return
    }

    setAssignments((current) => current.filter((assignment) => assignment.id !== deleteId))
    setDeleteId(null)
    setNotice('Đã xóa phân công mẫu khỏi lịch nháp.')
  }

  const handleSubmitStatus = (status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PUBLISHED') => {
    setNotice(`Mock state: chuyển lịch sang trạng thái ${scheduleStatusLabels[status] ?? status}.`)
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

      {notice && <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</div>}

      <section className="grid gap-4 md:grid-cols-4">
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Thời gian</p><p className="mt-2 font-semibold">{detail.startDate} → {detail.endDate}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Trạng thái</p><p className="mt-2 font-semibold">{scheduleStatusLabels[detail.status]}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Phân công</p><p className="mt-2 font-semibold">{assignments.length}</p></CardContent></Card>
        <Card className="border-slate-200 bg-white shadow-sm"><CardContent className="p-4"><p className="text-sm text-slate-500">Cảnh báo</p><p className="mt-2 font-semibold">{detail.warnings.length}</p></CardContent></Card>
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-wrap gap-3">
            <Button onClick={openCreate}><CalendarDays className="mr-2 h-4 w-4" />Thêm phân công</Button>
            <Button variant="outline" onClick={() => handleSubmitStatus('DRAFT')}><Save className="mr-2 h-4 w-4" />Lưu bản nháp</Button>
            <Button variant="outline" onClick={() => handleSubmitStatus('PENDING_APPROVAL')}><Send className="mr-2 h-4 w-4" />Gửi duyệt</Button>
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
            <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-500">Chế độ chỉnh sửa mock</div>
          </div>

          <div className="space-y-3">
            {assignments.map((assignment) => (
              <div key={assignment.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="font-medium text-slate-900">{assignment.shiftName} • {assignment.date}</p>
                  <p className="text-sm text-slate-500">{assignment.startTime} - {assignment.endTime} • {assignment.employeeNames.join(', ')}</p>
                  {assignment.warningCount > 0 && <p className="mt-1 inline-flex items-center gap-2 text-xs text-amber-700"><AlertTriangle className="h-3.5 w-3.5" />Có cảnh báo mẫu</p>}
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => openEdit(assignment)}>Sửa</Button>
                  <Button variant="outline" size="sm" onClick={() => setDeleteId(assignment.id)} className="text-red-600"> <Trash2 className="mr-2 h-4 w-4" />Xóa</Button>
                </div>
              </div>
            ))}
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
        description="Chi tiết dưới đây chỉ cập nhật mock state để kiểm thử giao diện." 
        onCancel={() => setDialogOpen(false)}
        onConfirm={handleSave}
        confirmLabel="Lưu"
      >
        <div className="mt-4 grid gap-4">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Ngày làm việc</label>
            <input type="date" value={formState.date} onChange={(event) => setFormState((current) => ({ ...current, date: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Loại ca</label>
            <select value={formState.shiftCode} onChange={(event) => setFormState((current) => ({ ...current, shiftCode: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
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
              {shiftOptions.find((shift) => shift.value === formState.shiftCode)?.label.split('•')[1]?.trim() ?? '07:00 - 12:00'}
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
        <Button onClick={() => handleSubmitStatus('PUBLISHED')}><UserRound className="mr-2 h-4 w-4" />Gửi duyệt</Button>
      </div>
    </div>
  )
}

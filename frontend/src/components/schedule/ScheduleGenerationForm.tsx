import { useMemo, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { employeeSeed } from '@/mocks/managementData'
import { shiftTypeSeed } from '@/mocks/managementData'
import { EMPLOYEE_PAGE_SIZE, getDefaultGenerationDates, getPaginatedEmployees, getTotalEmployeePages } from './ScheduleGenerationForm.helpers'
import type { ShiftType } from '@/types/management'

export interface ScheduleGenerationValues {
  name: string
  startDate: string
  endDate: string
  note: string
  period: 'WEEK' | 'MONTH'
  employeeIds: string[]
  shiftCodes: string[]
}

interface BackendGenerationEmployee {
  id: string
  fullName: string
  employeeCode: string
  aiSchedulable?: boolean
}

export interface BackendGenerationShiftType {
  id: number
  shiftName: string
  shiftCode: string | null
  startTime: string
  endTime: string
  status: string
}

interface GenerationShiftOption {
  id: string
  code: string
  name: string
  startTime: string
  endTime: string
  color: string
  description: string
}

interface ScheduleGenerationFormProps {
  onSubmit: (values: ScheduleGenerationValues) => void
  onCancel: () => void
  backendMode?: boolean
  backendEmployees?: BackendGenerationEmployee[]
  backendShiftTypes?: BackendGenerationShiftType[]
}


export function ScheduleGenerationForm({ onSubmit, onCancel, backendMode = false, backendEmployees = [], backendShiftTypes = [] }: ScheduleGenerationFormProps) {
  const activeEmployees = useMemo(() => backendMode ? backendEmployees.filter((employee) => employee.aiSchedulable === true) : employeeSeed.filter((employee) => employee.status === 'ACTIVE'), [backendMode, backendEmployees])
  const excludedBackendEmployeeCount = backendMode ? backendEmployees.length - activeEmployees.length : 0
  const activeShifts = useMemo(() => backendMode ? backendShiftTypes : shiftTypeSeed.filter((shift) => shift.status === 'ACTIVE'), [backendMode, backendShiftTypes])
  const shiftOptions = useMemo<GenerationShiftOption[]>(() => activeShifts.map((shift) => backendMode ? {
    id: String((shift as BackendGenerationShiftType).id),
    code: (shift as BackendGenerationShiftType).shiftCode ?? String((shift as BackendGenerationShiftType).id),
    name: (shift as BackendGenerationShiftType).shiftName,
    startTime: (shift as BackendGenerationShiftType).startTime,
    endTime: (shift as BackendGenerationShiftType).endTime,
    color: '#2563eb',
    description: 'Backend shift type',
  } : {
    id: (shift as ShiftType).code,
    code: (shift as ShiftType).code,
    name: (shift as ShiftType).name,
    startTime: (shift as ShiftType).startTime,
    endTime: (shift as ShiftType).endTime,
    color: (shift as ShiftType).color,
    description: (shift as ShiftType).applicableDayType === 'WEEKDAY' ? 'Thứ Hai đến Thứ Sáu' : 'Thứ Bảy, Chủ nhật & ngày lễ',
  }), [activeShifts, backendMode])
  const [name, setName] = useState('')
  const [startDate, setStartDate] = useState(() => getDefaultGenerationDates().startDate)
  const [endDate, setEndDate] = useState(() => getDefaultGenerationDates().endDate)
  const [note, setNote] = useState('')
  const [period, setPeriod] = useState<'WEEK' | 'MONTH'>('WEEK')
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<string[] | null>(null)
  const [employeeSearch, setEmployeeSearch] = useState('')
  const [employeePage, setEmployeePage] = useState(1)
  const [selectedShiftCodes, setSelectedShiftCodes] = useState<string[] | null>(null)
  const [error, setError] = useState('')
  const selectableEmployeeIds = activeEmployees.map((employee) => employee.id)
  const employeeIds = (selectedEmployeeIds ?? selectableEmployeeIds).filter((id) => selectableEmployeeIds.includes(id))
  const selectableShiftCodes = shiftOptions.map((shift) => shift.id)
  const shiftCodes = (selectedShiftCodes ?? selectableShiftCodes).filter((id) => selectableShiftCodes.includes(id))
  const visibleEmployees = activeEmployees.filter((employee) => [employee.fullName, employee.employeeCode].some((value) => value.toLowerCase().includes(employeeSearch.toLowerCase())))
  const totalEmployeePages = getTotalEmployeePages(visibleEmployees, EMPLOYEE_PAGE_SIZE)
  const employeePageItems = getPaginatedEmployees(visibleEmployees, employeePage, EMPLOYEE_PAGE_SIZE)

  const toggleValue = (values: string[], value: string, setter: (next: string[]) => void) => {
    setter(values.includes(value) ? values.filter((item) => item !== value) : [...values, value])
  }

  const handleSubmit = () => {
    if (!name.trim() || !startDate || !endDate) {
      setError('Vui lòng nhập tên lịch, ngày bắt đầu và ngày kết thúc.')
      return
    }
    if (endDate < startDate) {
      setError('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
      return
    }
    if (!employeeIds.length || !shiftCodes.length) {
      setError('Vui lòng chọn ít nhất một nhân viên và một loại ca.')
      return
    }

    setError('')
      onSubmit({ name: name.trim(), startDate, endDate, note: note.trim(), period, employeeIds, shiftCodes })
  }

  const resetForm = () => {
    const defaults = getDefaultGenerationDates()
    setName('')
    setStartDate(defaults.startDate)
    setEndDate(defaults.endDate)
    setNote('')
    setPeriod('WEEK')
    setSelectedEmployeeIds(null)
    setEmployeeSearch('')
    setEmployeePage(1)
    setSelectedShiftCodes(null)
    setError('')
  }

  return (
    <div className="space-y-6">
      <Card className="border-slate-200 shadow-sm">
        <CardHeader><CardTitle className="text-base">Thông tin chung</CardTitle></CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <label className="space-y-1 text-sm text-slate-700 md:col-span-2">Tên lịch<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ví dụ: Lịch vận hành tuần 4" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none" /></label>
          <label className="space-y-1 text-sm text-slate-700">Ngày bắt đầu<input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none" /></label>
          <label className="space-y-1 text-sm text-slate-700">Ngày kết thúc<input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none" /></label>
          <label className="space-y-1 text-sm text-slate-700">Chế độ lập lịch<select value={period} onChange={(event) => setPeriod(event.target.value as 'WEEK' | 'MONTH')} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none"><option value="WEEK">Theo tuần</option><option value="MONTH">Theo tháng</option></select></label>
          <label className="space-y-1 text-sm text-slate-700 md:col-span-2">Ghi chú<textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 focus:border-blue-500 focus:outline-none" /></label>
        </CardContent>
      </Card>

      {backendMode && <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">Backend sẽ tạo kỳ lịch, tạo ca làm việc theo loại ca đã chọn, rồi gọi AI Optimize để phân công nhân viên.</div>}

      <Card className="border-slate-200 shadow-sm">
        <CardHeader><div className="flex flex-wrap items-center justify-between gap-3"><CardTitle className="text-base">Phạm vi nhân viên <span className="text-sm font-normal text-slate-500">({backendMode ? `${activeEmployees.length} đủ điều kiện chạy AI` : `${activeEmployees.length} đang hoạt động`})</span></CardTitle><Button type="button" variant="outline" size="sm" onClick={() => setSelectedEmployeeIds(employeeIds.length === activeEmployees.length ? [] : activeEmployees.map((employee) => employee.id))}>{employeeIds.length === activeEmployees.length ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}</Button></div><input value={employeeSearch} onChange={(event) => { setEmployeeSearch(event.target.value); setEmployeePage(1) }} placeholder="Tìm theo tên hoặc mã nhân viên" className="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />{excludedBackendEmployeeCount > 0 && <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">Có {excludedBackendEmployeeCount} nhân viên đang hoạt động nhưng chưa có tài khoản employee active nên AI sẽ không dùng để chia lịch.</p>}</CardHeader>
        <CardContent><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{employeePageItems.map((employee) => <label key={employee.id} className="flex items-center gap-3 rounded-xl border border-slate-200 p-3 text-sm text-slate-700"><input type="checkbox" checked={employeeIds.includes(employee.id)} onChange={() => toggleValue(employeeIds, employee.id, setSelectedEmployeeIds)} /><span className="min-w-0 flex-1 truncate">{employee.fullName}</span>{backendMode && <Badge variant="secondary">AI dùng được</Badge>}<span className="text-xs text-slate-400">{employee.employeeCode}</span></label>)}</div>{visibleEmployees.length > EMPLOYEE_PAGE_SIZE && (<div className="mt-3 flex items-center justify-end gap-2"><Button type="button" variant="outline" size="sm" disabled={employeePage <= 1} onClick={() => setEmployeePage((current) => Math.max(1, current - 1))}>Trước</Button><span className="text-sm text-slate-500">Trang {employeePage}/{totalEmployeePages}</span><Button type="button" variant="outline" size="sm" disabled={employeePage >= totalEmployeePages} onClick={() => setEmployeePage((current) => Math.min(totalEmployeePages, current + 1))}>Sau</Button></div>)}</CardContent>
      </Card>

      <Card className="border-slate-200 shadow-sm">
        <CardHeader><CardTitle className="text-base">Loại ca áp dụng</CardTitle></CardHeader>
        <CardContent className="grid gap-3 lg:grid-cols-3">{shiftOptions.map((shift) => {
          return <label key={shift.id} className="cursor-pointer rounded-xl border p-4" style={{ borderLeftColor: shift.color, borderLeftWidth: 4 }}><div className="flex items-start gap-3"><input type="checkbox" checked={shiftCodes.includes(shift.id)} onChange={() => toggleValue(shiftCodes, shift.id, setSelectedShiftCodes)} /><div><p className="font-semibold text-slate-900">{shift.name} <span className="text-xs font-normal text-slate-500">({shift.code})</span></p><p className="mt-1 text-sm text-slate-500">{shift.startTime} - {shift.endTime} · {shift.description}</p></div></div></label>
        })}</CardContent>
      </Card>

      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <div className="flex flex-wrap justify-end gap-3"><Button type="button" variant="outline" onClick={onCancel}>Hủy</Button><Button type="button" variant="outline" onClick={resetForm}>Đặt lại</Button><Button type="button" onClick={handleSubmit}>Tạo lịch tự động</Button></div>
    </div>
  )
}

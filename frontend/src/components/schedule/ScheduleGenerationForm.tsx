import { useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { employeeSeed } from '@/mocks/managementData'
import { shiftTypeSeed } from '@/mocks/managementData'

export interface ScheduleGenerationValues {
  name: string
  startDate: string
  endDate: string
  note: string
  period: 'WEEK' | 'MONTH'
  employeeIds: string[]
  shiftCodes: string[]
  weights: { fairness: number; consecutiveShift: number; weekend: number; preference: number; workHour: number }
}

interface ScheduleGenerationFormProps {
  onSubmit: (values: ScheduleGenerationValues) => void
  onCancel: () => void
}

const initialWeights = { fairness: 5, consecutiveShift: 4, weekend: 3, preference: 6, workHour: 4 }

export function ScheduleGenerationForm({ onSubmit, onCancel }: ScheduleGenerationFormProps) {
  const activeEmployees = useMemo(() => employeeSeed.filter((employee) => employee.status === 'ACTIVE'), [])
  const activeShifts = useMemo(() => shiftTypeSeed.filter((shift) => shift.status === 'ACTIVE'), [])
  const [name, setName] = useState('')
  const [startDate, setStartDate] = useState('2026-09-21')
  const [endDate, setEndDate] = useState('2026-09-27')
  const [note, setNote] = useState('')
  const [period, setPeriod] = useState<'WEEK' | 'MONTH'>('WEEK')
  const [employeeIds, setEmployeeIds] = useState<string[]>(activeEmployees.map((employee) => employee.id))
  const [employeeSearch, setEmployeeSearch] = useState('')
  const [shiftCodes, setShiftCodes] = useState<string[]>(activeShifts.map((shift) => shift.code))
  const [weights, setWeights] = useState(initialWeights)
  const [error, setError] = useState('')
  const visibleEmployees = activeEmployees.filter((employee) => [employee.fullName, employee.employeeCode].some((value) => value.toLowerCase().includes(employeeSearch.toLowerCase())))

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
    onSubmit({ name: name.trim(), startDate, endDate, note: note.trim(), period, employeeIds, shiftCodes, weights })
  }

  const resetForm = () => {
    setName('')
    setStartDate('2026-09-21')
    setEndDate('2026-09-27')
    setNote('')
    setPeriod('WEEK')
    setEmployeeIds(activeEmployees.map((employee) => employee.id))
    setEmployeeSearch('')
    setShiftCodes(activeShifts.map((shift) => shift.code))
    setWeights(initialWeights)
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

      <Card className="border-slate-200 shadow-sm">
        <CardHeader><div className="flex flex-wrap items-center justify-between gap-3"><CardTitle className="text-base">Phạm vi nhân viên <span className="text-sm font-normal text-slate-500">({activeEmployees.length} đang hoạt động)</span></CardTitle><Button type="button" variant="outline" size="sm" onClick={() => setEmployeeIds(employeeIds.length === activeEmployees.length ? [] : activeEmployees.map((employee) => employee.id))}>{employeeIds.length === activeEmployees.length ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}</Button></div><input value={employeeSearch} onChange={(event) => setEmployeeSearch(event.target.value)} placeholder="Tìm theo tên hoặc mã nhân viên" className="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" /></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{visibleEmployees.map((employee) => <label key={employee.id} className="flex items-center gap-3 rounded-xl border border-slate-200 p-3 text-sm text-slate-700"><input type="checkbox" checked={employeeIds.includes(employee.id)} onChange={() => toggleValue(employeeIds, employee.id, setEmployeeIds)} />{employee.fullName}<span className="ml-auto text-xs text-slate-400">{employee.employeeCode}</span></label>)}</CardContent>
      </Card>

      <Card className="border-slate-200 shadow-sm">
        <CardHeader><CardTitle className="text-base">Loại ca áp dụng</CardTitle></CardHeader>
        <CardContent className="grid gap-3 lg:grid-cols-3">{activeShifts.map((shift) => <label key={shift.code} className="cursor-pointer rounded-xl border p-4" style={{ borderLeftColor: shift.color, borderLeftWidth: 4 }}><div className="flex items-start gap-3"><input type="checkbox" checked={shiftCodes.includes(shift.code)} onChange={() => toggleValue(shiftCodes, shift.code, setShiftCodes)} /><div><p className="font-semibold text-slate-900">{shift.name} <span className="text-xs font-normal text-slate-500">({shift.code})</span></p><p className="mt-1 text-sm text-slate-500">{shift.startTime} - {shift.endTime} · {shift.minEmployees}-{shift.maxEmployees} nhân viên</p></div></div></label>)}</CardContent>
      </Card>

      <Card className="border-slate-200 shadow-sm">
        <CardHeader><CardTitle className="text-base">Các ràng buộc và tiêu chí tối ưu</CardTitle></CardHeader>
        <CardContent className="grid gap-5 lg:grid-cols-2"><div><p className="mb-2 text-sm font-semibold text-red-700">Ràng buộc cứng</p><ul className="list-disc space-y-1 pl-5 text-sm text-slate-600"><li>Không trùng ca.</li><li>Tối đa 12 giờ làm việc/ngày.</li><li>Nghỉ tối thiểu 12 giờ giữa hai ca.</li><li>Ngoài giờ tối đa 1 ngày/tuần.</li><li>Thông tầm tối đa 2 ngày/tuần.</li><li>Không phân công vào ngày nghỉ đã duyệt.</li></ul></div><div><p className="mb-2 text-sm font-semibold text-amber-700">Tiêu chí tối ưu</p><div className="space-y-3">{Object.entries({ fairness: 'Công bằng tổng số ca', consecutiveShift: 'Hạn chế ca nặng liên tiếp', weekend: 'Cân bằng ca cuối tuần', preference: 'Ưu tiên nguyện vọng', workHour: 'Cân bằng tổng giờ làm' }).map(([key, label]) => <label key={key} className="flex items-center justify-between gap-3 text-sm text-slate-700">{label}<input type="number" min={0} value={weights[key as keyof typeof weights]} onChange={(event) => setWeights((current) => ({ ...current, [key]: Number(event.target.value) }))} className="w-20 rounded-lg border border-slate-200 px-2 py-1.5 text-right" /></label>)}</div><p className="mt-3 text-xs text-slate-500">Trọng số chỉ được lưu trong form và sẽ gửi cho dịch vụ lập lịch khi tích hợp backend. Không phải phần trăm.</p></div></CardContent>
      </Card>

      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <div className="flex flex-wrap justify-end gap-3"><Button type="button" variant="outline" onClick={onCancel}>Hủy</Button><Button type="button" variant="outline" onClick={resetForm}>Đặt lại</Button><Button type="button" onClick={handleSubmit}>Tạo lịch tự động</Button></div>
    </div>
  )
}

import { useState } from 'react'

import { FormDialog } from '@/components/common/FormDialog'
import type { CreateEmployeeAccountInput } from '@/types/account'
import type { Employee } from '@/types/management'

interface CreateAccountDialogProps {
  employee: Employee | null
  employees: Employee[]
  open: boolean
  isSubmitting: boolean
  onClose: () => void
  onSubmit: (payload: CreateEmployeeAccountInput) => Promise<void> | void
}

export function CreateAccountDialog({ employee, employees, open, isSubmitting, onClose, onSubmit }: CreateAccountDialogProps) {
  const initialEmployee = employee ?? employees[0] ?? null
  const [form, setForm] = useState(() => ({
    username: '',
  }))
  const [error, setError] = useState('')
  const [selectedEmployeeId, setSelectedEmployeeId] = useState(initialEmployee?.id ?? '')

  const selectedEmployee = employees.find((item) => item.id === selectedEmployeeId) ?? initialEmployee ?? null

  if (!open || !selectedEmployee) {
    return null
  }

  const handleSubmit = async () => {
    if (!selectedEmployee.email.trim()) {
      setError('Nhân viên chưa có email. Vui lòng cập nhật email trong hồ sơ nhân viên trước khi tạo tài khoản.')
      return
    }

    const username = form.username.trim()

    if (!username) {
      setError('Tên tài khoản là bắt buộc.')
      return
    }

    if (/\s/.test(username)) {
      setError('Tên tài khoản không được chứa khoảng trắng.')
      return
    }

    setError('')
    await onSubmit({
      employeeId: selectedEmployee.id,
      username,
    })
  }

  return (
    <FormDialog
      open={open}
      title="Tạo tài khoản nhân viên"
      description="Tạo tài khoản cho nhân viên với trạng thái chờ kích hoạt."
      onClose={onClose}
      onSubmit={handleSubmit}
      submitLabel={isSubmitting ? 'Đang tạo...' : 'Tạo tài khoản'}
    >
      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-1 md:col-span-2">
          <label className="text-sm font-medium text-slate-700">Chọn nhân viên chưa có tài khoản</label>
          <select
            value={selectedEmployeeId}
            onChange={(event) => {
              setSelectedEmployeeId(event.target.value)
            }}
            className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
          >
            {employees.map((item) => (
              <option key={item.id} value={item.id}>{item.employeeCode} - {item.fullName}</option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-slate-700">Mã nhân viên</label>
          <input value={selectedEmployee.employeeCode} readOnly className="w-full rounded-xl border border-slate-200 bg-slate-100 px-3 py-2.5 text-sm text-slate-600" />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-slate-700">Họ tên nhân viên</label>
          <input value={selectedEmployee.fullName} readOnly className="w-full rounded-xl border border-slate-200 bg-slate-100 px-3 py-2.5 text-sm text-slate-600" />
        </div>
        <div className="space-y-1 md:col-span-2">
          <p className="text-sm font-medium text-slate-700">Email nhân viên</p>
          <p className="rounded-xl border border-slate-200 bg-slate-100 px-3 py-2.5 text-sm text-slate-600">{selectedEmployee.email}</p>
          {!selectedEmployee.email && (
            <p className="text-xs text-amber-700">Nhân viên chưa có email, không thể tạo tài khoản cho đến khi hồ sơ được cập nhật.</p>
          )}
        </div>
        <div className="space-y-1 md:col-span-2">
          <label className="text-sm font-medium text-slate-700">Tên tài khoản</label>
          <input
            value={form.username}
            onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
            className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none"
            autoComplete="username"
            placeholder="vd: hoangtam"
          />
        </div>
      </div>

      <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
        <p className="font-medium text-slate-700">Vai trò</p>
        <p className="mt-1">Nhân viên</p>
      </div>

      {error && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
    </FormDialog>
  )
}

import { AlertTriangle, Edit3, Eye, Search, Trash2, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

import { AccountStatusBadge } from '@/components/account/AccountStatusBadge'
import { CreateAccountDialog } from '@/components/account/CreateAccountDialog'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
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
import { useAuth } from '@/contexts/useAuth'
import { accountService } from '@/services/accountService'
import { employeeService } from '@/services/employeeService'
import type { UserAccount } from '@/types/account'
import type { Employee, EmployeeFormValues, PaginatedResult } from '@/types/management'

const emptyForm: EmployeeFormValues = {
  employeeCode: '',
  fullName: '',
  email: '',
  phone: '',
  role: 'EMPLOYEE',
  status: 'ACTIVE',
  joinDate: new Date().toISOString().slice(0, 10),
  note: '',
}

const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'ACTIVE', label: 'Đang hoạt động' },
  { value: 'INACTIVE', label: 'Tạm ngưng' },
]

const getAccountStatus = (employeeId: string, accounts: UserAccount[]) => {
  const account = accounts.find((item) => item.employeeId === employeeId)
  return account ? account.status : 'NO_ACCOUNT'
}

export function EmployeesPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<Employee>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [accounts, setAccounts] = useState<UserAccount[]>([])
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null)
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [createAccountTarget, setCreateAccountTarget] = useState<Employee | null>(null)
  const [accountDialogOpen, setAccountDialogOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Employee | null>(null)
  const [resendTarget, setResendTarget] = useState<UserAccount | null>(null)
  const [followUpEmployee, setFollowUpEmployee] = useState<Employee | null>(null)
  const [formState, setFormState] = useState<EmployeeFormValues>(emptyForm)
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [accountSubmitting, setAccountSubmitting] = useState(false)

  const fetchEmployees = async () => {
    setLoading(true)
    setError('')

    try {
      const [employeeResult, accountResult] = await Promise.all([
        employeeService.list({ page, pageSize: 5, search, status }),
        accountService.getAccounts(),
      ])
      setData(employeeResult)
      setAccounts(accountResult)
    } catch (fetchError) {
      setError(fetchError instanceof Error ? fetchError.message : 'Không thể tải danh sách nhân viên.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const loadEmployees = async () => {
      setLoading(true)
      setError('')

      try {
        const [employeeResult, accountResult] = await Promise.all([
          employeeService.list({ page, pageSize: 5, search, status }),
          accountService.getAccounts(),
        ])

        if (mounted) {
          setData(employeeResult)
          setAccounts(accountResult)
        }
      } catch (fetchError) {
        if (mounted) {
          setError(fetchError instanceof Error ? fetchError.message : 'Không thể tải danh sách nhân viên.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadEmployees()

    return () => {
      mounted = false
    }
  }, [page, search, status])

  const stats = useMemo(() => {
    const total = data.total
    const inactive = data.data.filter((employee) => employee.status === 'INACTIVE').length

    return {
      total,
      active: total - inactive,
      inactive,
    }
  }, [data])

  const validateForm = () => {
    if (!formState.employeeCode.trim()) return 'Mã nhân viên là bắt buộc.'
    if (!formState.fullName.trim()) return 'Họ tên là bắt buộc.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formState.email.trim())) return 'Email không hợp lệ.'
    if (!/^0\d{9,10}$/.test(formState.phone.trim())) return 'Số điện thoại không hợp lệ.'
    if (!formState.role) return 'Vai trò là bắt buộc.'

    return ''
  }

  const openCreate = () => {
    setEditingEmployee(null)
    setFormState(emptyForm)
    setFormError('')
    setFormOpen(true)
  }

  const openEdit = (employee: Employee) => {
    setEditingEmployee(employee)
    setFormState({
      employeeCode: employee.employeeCode,
      fullName: employee.fullName,
      email: employee.email,
      phone: employee.phone,
      role: employee.role,
      status: employee.status,
      joinDate: employee.joinDate,
      note: employee.note ?? '',
    })
    setFormError('')
    setFormOpen(true)
  }

  const handleSubmit = async () => {
    const validationMessage = validateForm()
    if (validationMessage) {
      setFormError(validationMessage)
      return
    }

    setIsSubmitting(true)
    setFormError('')

    try {
      let createdEmployee: Employee | null = null

      if (editingEmployee) {
        await employeeService.update(editingEmployee.id, formState)
      } else {
        createdEmployee = await employeeService.add(formState)
      }

      setFormOpen(false)
      setPage(1)
      await fetchEmployees()

      if (!editingEmployee && createdEmployee) {
        setFollowUpEmployee(createdEmployee)
      }
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : 'Có lỗi xảy ra khi lưu nhân viên.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const toggleStatus = async (employee: Employee) => {
    if (user?.id === employee.id) {
      window.alert('Bạn không thể tạm ngưng hoặc xóa tài khoản đang đăng nhập.')
      return
    }

    try {
      await employeeService.toggleStatus(employee.id, user?.id)
      await fetchEmployees()
    } catch (toggleError) {
      window.alert(toggleError instanceof Error ? toggleError.message : 'Không thể thay đổi trạng thái.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }

    if (user?.id === deleteTarget.id) {
      window.alert('Bạn không thể xóa tài khoản đang đăng nhập.')
      return
    }

    try {
      await employeeService.remove(deleteTarget.id, user?.id)
      setDeleteTarget(null)
      setPage(1)
      await fetchEmployees()
    } catch (deleteError) {
      window.alert(deleteError instanceof Error ? deleteError.message : 'Không thể xóa nhân viên.')
    }
  }

  const handleCreateAccountSubmit = async (input: { employeeId: string; username: string; email: string; temporaryPassword: string }) => {
    setAccountSubmitting(true)

    try {
      await accountService.createEmployeeAccount(input)
      setAccountDialogOpen(false)
      setCreateAccountTarget(null)
      await fetchEmployees()
    } finally {
      setAccountSubmitting(false)
    }
  }

  const handleResendOtp = async () => {
    if (!resendTarget) {
      return
    }

    try {
      await accountService.resendActivationOtp(resendTarget.id)
      setResendTarget(null)
      await fetchEmployees()
      window.alert('Mã OTP mới đã được gửi tới email nhân viên. OTP mock đang sử dụng: 123456.')
    } catch (errorMessage) {
      window.alert(errorMessage instanceof Error ? errorMessage.message : 'Không thể gửi lại mã OTP.')
    }
  }

  const openAccountDialog = (employee: Employee) => {
    setCreateAccountTarget(employee)
    setAccountDialogOpen(true)
  }

  const renderAccountColumn = (employee: Employee) => {
    const status = getAccountStatus(employee.id, accounts)
    return <AccountStatusBadge status={status} />
  }

  const renderAccountAction = (employee: Employee) => {
    const status = getAccountStatus(employee.id, accounts)
    const account = accounts.find((item) => item.employeeId === employee.id)

    if (status === 'NO_ACCOUNT') {
      return <Button variant="outline" size="sm" onClick={() => openAccountDialog(employee)}>Tạo tài khoản</Button>
    }

    if (status === 'PENDING_ACTIVATION') {
      return <Button variant="secondary" size="sm" onClick={() => setResendTarget(account ?? null)}>Gửi lại OTP</Button>
    }

    if (status === 'ACTIVE') {
      return <Button variant="ghost" size="sm" onClick={() => setSelectedEmployee(employee)}>Xem tài khoản</Button>
    }

    if (status === 'LOCKED' || status === 'DISABLED') {
      return <AccountStatusBadge status={status} />
    }

    return null
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý nhân viên" description="Theo dõi và quản lý danh sách nhân viên trong hệ thống." onAction={openCreate} actionLabel="Thêm nhân viên" />

      <section className="grid gap-4 md:grid-cols-3">
        <StatCard label="Tổng nhân viên" value={data.total} icon={<UserRound className="h-5 w-5" />} tone="blue" />
        <StatCard label="Đang hoạt động" value={stats.active} icon={<Search className="h-5 w-5" />} tone="green" />
        <StatCard label="Tạm ngưng" value={stats.inactive} icon={<AlertTriangle className="h-5 w-5" />} tone="red" />
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="w-full md:max-w-md">
              <SearchInput value={search} placeholder="Tìm theo mã, tên, email hoặc số điện thoại" onChange={setSearch} />
            </div>
            <select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value)
                setPage(1)
              }}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 focus:border-blue-500 focus:outline-none"
            >
              {statusOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>

          {loading ? (
            <LoadingState label="Đang tải danh sách nhân viên..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchEmployees} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có nhân viên nào" description="Thử thay đổi từ khóa tìm kiếm hoặc thêm nhân viên mới." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'employeeCode', label: 'Mã NV' },
                  { key: 'fullName', label: 'Họ và tên' },
                  { key: 'email', label: 'Email' },
                  { key: 'phone', label: 'Số điện thoại' },
                  { key: 'role', label: 'Vai trò', render: (employee: Employee) => <span>{employee.role === 'MANAGER' ? 'Quản lý' : 'Nhân viên'}</span> },
                  { key: 'accountStatus', label: 'Trạng thái tài khoản', render: renderAccountColumn },
                  { key: 'status', label: 'Trạng thái', render: (employee: Employee) => <StatusBadge status={employee.status} /> },
                  { key: 'joinDate', label: 'Ngày bắt đầu', render: (employee: Employee) => <span>{new Date(employee.joinDate).toLocaleDateString('vi-VN')}</span> },
                  { key: 'actions', label: 'Thao tác', render: (employee: Employee) => (
                    <div className="flex flex-wrap items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelectedEmployee(employee)} aria-label="Xem chi tiết">
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => openEdit(employee)} aria-label="Chỉnh sửa nhân viên">
                        <Edit3 className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => toggleStatus(employee)} aria-label="Thay đổi trạng thái">
                        <AlertTriangle className="h-4 w-4" />
                      </Button>
                      {renderAccountAction(employee)}
                      <Button variant="ghost" size="icon" onClick={() => setDeleteTarget(employee)} aria-label="Xóa nhân viên">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ) },
                ]}
                emptyState={<EmptyState title="Không có dữ liệu" description="Hiện chưa có nhân viên nào ở trạng thái này." />}
              />

              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <FormDialog
        open={formOpen}
        title={editingEmployee ? 'Chỉnh sửa nhân viên' : 'Thêm nhân viên'}
        description="Thông tin dưới đây được lưu trong mock data để kiểm thử giao diện."
        onClose={() => setFormOpen(false)}
        onSubmit={handleSubmit}
        submitLabel={isSubmitting ? 'Đang lưu...' : 'Lưu'}
      >
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Mã nhân viên</label>
            <input value={formState.employeeCode} onChange={(event) => setFormState((current) => ({ ...current, employeeCode: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Họ và tên</label>
            <input value={formState.fullName} onChange={(event) => setFormState((current) => ({ ...current, fullName: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Email</label>
            <input type="email" value={formState.email} onChange={(event) => setFormState((current) => ({ ...current, email: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Số điện thoại</label>
            <input value={formState.phone} onChange={(event) => setFormState((current) => ({ ...current, phone: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Vai trò</label>
            <select value={formState.role} onChange={(event) => setFormState((current) => ({ ...current, role: event.target.value as 'MANAGER' | 'EMPLOYEE' }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
              <option value="MANAGER">Quản lý</option>
              <option value="EMPLOYEE">Nhân viên</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Trạng thái</label>
            <select value={formState.status} onChange={(event) => setFormState((current) => ({ ...current, status: event.target.value as 'ACTIVE' | 'INACTIVE' }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="INACTIVE">Tạm ngưng</option>
            </select>
          </div>
          <div className="space-y-1 md:col-span-2">
            <label className="text-sm text-slate-700">Ngày bắt đầu làm việc</label>
            <input type="date" value={formState.joinDate} onChange={(event) => setFormState((current) => ({ ...current, joinDate: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1 md:col-span-2">
            <label className="text-sm text-slate-700">Ghi chú</label>
            <textarea value={formState.note} onChange={(event) => setFormState((current) => ({ ...current, note: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
        </div>

        {formError && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</div>}
      </FormDialog>

      <CreateAccountDialog
        employee={createAccountTarget}
        employees={data.data.filter((employee) => getAccountStatus(employee.id, accounts) === 'NO_ACCOUNT')}
        open={accountDialogOpen}
        isSubmitting={accountSubmitting}
        onClose={() => {
          setAccountDialogOpen(false)
          setCreateAccountTarget(null)
        }}
        onSubmit={handleCreateAccountSubmit}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Xóa nhân viên"
        description={`Bạn có chắc chắn muốn xóa ${deleteTarget?.fullName} khỏi danh sách nhân viên?`}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
      />

      <ConfirmDialog
        open={Boolean(resendTarget)}
        title="Gửi lại OTP"
        description={`Bạn có muốn gửi lại mã kích hoạt cho tài khoản của ${resendTarget ? (accounts.find((account) => account.id === resendTarget.id)?.username ?? 'nhân viên') : 'nhân viên'}?`}
        cancelLabel="Hủy"
        confirmLabel="Gửi lại OTP"
        onCancel={() => setResendTarget(null)}
        onConfirm={handleResendOtp}
      >
        <p className="mt-3 text-sm text-slate-500">Email nhận mã: {resendTarget ? accounts.find((account) => account.id === resendTarget.id)?.email ? accounts.find((account) => account.id === resendTarget.id)?.email?.replace(/(.{2})(.*)(@.*)/, '$1***$3') : '' : ''}</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(followUpEmployee)}
        title="Đã thêm nhân viên thành công"
        description={`Bạn có muốn tạo tài khoản cho nhân viên ${followUpEmployee?.fullName} ngay không?`}
        cancelLabel="Để sau"
        confirmLabel="Tạo tài khoản"
        onCancel={() => setFollowUpEmployee(null)}
        onConfirm={() => {
          if (followUpEmployee) {
            setAccountDialogOpen(true)
            setCreateAccountTarget(followUpEmployee)
          }
          setFollowUpEmployee(null)
        }}
      />

      {selectedEmployee && (
        <FormDialog open={Boolean(selectedEmployee)} title="Chi tiết nhân viên" description="Xem thông tin nhân viên đang chọn." onClose={() => setSelectedEmployee(null)} onSubmit={() => setSelectedEmployee(null)} submitLabel="Đóng">
          <div className="space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã nhân viên</span><strong>{selectedEmployee.employeeCode}</strong></div>
            <div className="flex justify-between"><span>Họ tên</span><strong>{selectedEmployee.fullName}</strong></div>
            <div className="flex justify-between"><span>Email</span><strong>{selectedEmployee.email}</strong></div>
            <div className="flex justify-between"><span>Số điện thoại</span><strong>{selectedEmployee.phone}</strong></div>
            <div className="flex justify-between"><span>Vai trò</span><strong>{selectedEmployee.role === 'MANAGER' ? 'Quản lý' : 'Nhân viên'}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selectedEmployee.status} /></div>
            <div className="flex justify-between"><span>Ngày bắt đầu</span><strong>{new Date(selectedEmployee.joinDate).toLocaleDateString('vi-VN')}</strong></div>
            <div className="flex justify-between"><span>Ghi chú</span><strong>{selectedEmployee.note || 'Không có'}</strong></div>
            <div className="flex justify-between"><span>Trạng thái tài khoản</span><AccountStatusBadge status={getAccountStatus(selectedEmployee.id, accounts)} /></div>
          </div>
        </FormDialog>
      )}
    </div>
  )
}

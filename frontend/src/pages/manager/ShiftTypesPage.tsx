import { Clock3, Edit3, Eye, Palette, Search, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

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
import { shiftTypeService } from '@/services/shiftTypeService'
import type { PaginatedResult, ShiftType, ShiftTypeFormValues } from '@/types/management'

const emptyForm: ShiftTypeFormValues = {
  code: '',
  name: '',
  startTime: '07:00',
  endTime: '12:00',
  color: '#2563eb',
  minEmployees: 1,
  maxEmployees: 3,
  description: '',
  status: 'ACTIVE',
}

const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'ACTIVE', label: 'Đang hoạt động' },
  { value: 'INACTIVE', label: 'Tạm ngưng' },
]

export function ShiftTypesPage() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<ShiftType>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
  const [formOpen, setFormOpen] = useState(false)
  const [selectedShift, setSelectedShift] = useState<ShiftType | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ShiftType | null>(null)
  const [formState, setFormState] = useState<ShiftTypeFormValues>(emptyForm)
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await shiftTypeService.list({ page, pageSize: 5, search, status })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách loại ca.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const loadShiftTypes = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await shiftTypeService.list({ page, pageSize: 5, search, status })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách loại ca.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadShiftTypes()

    return () => {
      mounted = false
    }
  }, [page, search, status])

  const stats = useMemo(() => {
    const active = data.data.filter((item) => item.status === 'ACTIVE').length
    const inactive = data.data.filter((item) => item.status === 'INACTIVE').length

    return { active, inactive }
  }, [data])

  const validateForm = () => {
    if (!formState.code.trim()) return 'Mã ca là bắt buộc.'
    if (!formState.name.trim()) return 'Tên ca là bắt buộc.'
    if (!formState.startTime || !formState.endTime) return 'Giờ bắt đầu và kết thúc là bắt buộc.'
    if (Number(formState.minEmployees) <= 0) return 'Số nhân viên tối thiểu phải lớn hơn 0.'
    if (Number(formState.maxEmployees) < Number(formState.minEmployees)) return 'Số tối đa không được nhỏ hơn số tối thiểu.'

    return ''
  }

  const openCreate = () => {
    setSelectedShift(null)
    setFormState(emptyForm)
    setFormError('')
    setFormOpen(true)
  }

  const openEdit = (shiftType: ShiftType) => {
    setSelectedShift(shiftType)
    setFormState({
      code: shiftType.code,
      name: shiftType.name,
      startTime: shiftType.startTime,
      endTime: shiftType.endTime,
      color: shiftType.color,
      minEmployees: shiftType.minEmployees,
      maxEmployees: shiftType.maxEmployees,
      description: shiftType.description,
      status: shiftType.status,
    })
    setFormError('')
    setFormOpen(true)
  }

  const handleSubmit = async () => {
    const validation = validateForm()
    if (validation) {
      setFormError(validation)
      return
    }

    setIsSubmitting(true)
    setFormError('')

    try {
      if (selectedShift) {
        await shiftTypeService.update(selectedShift.id, formState)
      } else {
        await shiftTypeService.add(formState)
      }

      setFormOpen(false)
      setPage(1)
      await fetchData()
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : 'Không thể lưu loại ca.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const toggleStatus = async (shiftType: ShiftType) => {
    try {
      await shiftTypeService.toggleStatus(shiftType.id)
      await fetchData()
    } catch (toggleError) {
      window.alert(toggleError instanceof Error ? toggleError.message : 'Không thể thay đổi trạng thái ca.')
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) {
      return
    }

    try {
      await shiftTypeService.remove(deleteTarget.id)
      setDeleteTarget(null)
      setPage(1)
      await fetchData()
    } catch (deleteError) {
      window.alert(deleteError instanceof Error ? deleteError.message : 'Không thể xóa loại ca.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý loại ca trực" description="Quản lý các ca làm việc và quy định phân tổ lịch." onAction={openCreate} actionLabel="Thêm loại ca" />

      <section className="grid gap-4 md:grid-cols-2">
        <StatCard label="Đang hoạt động" value={stats.active} icon={<Clock3 className="h-5 w-5" />} tone="blue" />
        <StatCard label="Tạm ngưng" value={stats.inactive} icon={<Palette className="h-5 w-5" />} tone="amber" />
      </section>

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="w-full md:max-w-md">
              <SearchInput value={search} placeholder="Tìm theo tên hoặc mã ca" onChange={setSearch} />
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
            <LoadingState label="Đang tải loại ca trực..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có loại ca trực nào" description="Thử thay đổi bộ lọc hoặc thêm loại ca mới." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'code', label: 'Mã ca' },
                  { key: 'name', label: 'Tên ca' },
                  { key: 'startTime', label: 'Bắt đầu' },
                  { key: 'endTime', label: 'Kết thúc' },
                  { key: 'durationHours', label: 'Thời lượng (h)' },
                  { key: 'minEmployees', label: 'Tối thiểu' },
                  { key: 'maxEmployees', label: 'Tối đa' },
                  { key: 'status', label: 'Trạng thái', render: (item: ShiftType) => <StatusBadge status={item.status} /> },
                  { key: 'actions', label: 'Thao tác', render: (item: ShiftType) => (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon" onClick={() => setSelectedShift(item)} aria-label="Xem chi tiết">
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => openEdit(item)} aria-label="Chỉnh sửa ca">
                        <Edit3 className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => toggleStatus(item)} aria-label="Thay đổi trạng thái ca">
                        <Search className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => setDeleteTarget(item)} aria-label="Xóa ca">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ) },
                ]}
                emptyState={<EmptyState title="Không có dữ liệu" description="Hiện chưa có loại ca nào phù hợp." />}
              />
              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <FormDialog
        open={formOpen}
        title={selectedShift ? 'Chỉnh sửa loại ca' : 'Thêm loại ca'}
        description="Hỗ trợ ca kéo dài qua ngày hôm sau và hiển thị màu trên lịch."
        onClose={() => setFormOpen(false)}
        onSubmit={handleSubmit}
        submitLabel={isSubmitting ? 'Đang lưu...' : 'Lưu'}
      >
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Mã ca</label>
            <input value={formState.code} onChange={(event) => setFormState((current) => ({ ...current, code: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Tên ca</label>
            <input value={formState.name} onChange={(event) => setFormState((current) => ({ ...current, name: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Giờ bắt đầu</label>
            <input type="time" value={formState.startTime} onChange={(event) => setFormState((current) => ({ ...current, startTime: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Giờ kết thúc</label>
            <input type="time" value={formState.endTime} onChange={(event) => setFormState((current) => ({ ...current, endTime: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Màu hiển thị</label>
            <div className="flex items-center gap-3 rounded-xl border border-slate-200 px-3 py-2.5">
              <input type="color" value={formState.color} onChange={(event) => setFormState((current) => ({ ...current, color: event.target.value }))} className="h-10 w-12 rounded-md border-0 bg-transparent" />
              <span>{formState.color}</span>
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Trạng thái</label>
            <select value={formState.status} onChange={(event) => setFormState((current) => ({ ...current, status: event.target.value as 'ACTIVE' | 'INACTIVE' }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="INACTIVE">Tạm ngưng</option>
            </select>
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Số nhân viên tối thiểu</label>
            <input type="number" min={1} value={formState.minEmployees} onChange={(event) => setFormState((current) => ({ ...current, minEmployees: Number(event.target.value) }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1">
            <label className="text-sm text-slate-700">Số nhân viên tối đa</label>
            <input type="number" min={1} value={formState.maxEmployees} onChange={(event) => setFormState((current) => ({ ...current, maxEmployees: Number(event.target.value) }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
          <div className="space-y-1 md:col-span-2">
            <label className="text-sm text-slate-700">Mô tả</label>
            <textarea value={formState.description} onChange={(event) => setFormState((current) => ({ ...current, description: event.target.value }))} rows={3} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none" />
          </div>
        </div>

        {formError && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</div>}
      </FormDialog>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Xóa loại ca trực"
        description={`Loại ca ${deleteTarget?.name} đang được dùng trong mock schedule. Bạn nên tạm ngưng thay vì xóa ngay.`}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
      />

      {selectedShift && (
        <FormDialog open={Boolean(selectedShift)} title="Chi tiết loại ca" description="Thông tin chi tiết của loại ca trực." onClose={() => setSelectedShift(null)} onSubmit={() => setSelectedShift(null)} submitLabel="Đóng">
          <div className="space-y-3 text-sm text-slate-600">
            <div className="flex justify-between"><span>Mã ca</span><strong>{selectedShift.code}</strong></div>
            <div className="flex justify-between"><span>Tên ca</span><strong>{selectedShift.name}</strong></div>
            <div className="flex justify-between"><span>Thời gian</span><strong>{selectedShift.startTime} - {selectedShift.endTime}</strong></div>
            <div className="flex justify-between"><span>Thời lượng</span><strong>{selectedShift.durationHours} giờ</strong></div>
            <div className="flex justify-between"><span>Nhân viên tối thiểu</span><strong>{selectedShift.minEmployees}</strong></div>
            <div className="flex justify-between"><span>Nhân viên tối đa</span><strong>{selectedShift.maxEmployees}</strong></div>
            <div className="flex justify-between"><span>Trạng thái</span><StatusBadge status={selectedShift.status} /></div>
            <div className="flex justify-between"><span>Mô tả</span><strong>{selectedShift.description}</strong></div>
          </div>
        </FormDialog>
      )}
    </div>
  )
}

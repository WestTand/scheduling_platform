import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'

import { DataTable } from '@/components/common/DataTable'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { SearchInput } from '@/components/common/SearchInput'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { activityLogService } from '@/services/activityLogService'
import type { ActivityLog, PaginatedResult } from '@/types/management'

const actionOptions = [
  { value: 'ALL', label: 'Tất cả hành động' },
  { value: 'APPROVE_SCHEDULE', label: 'Duyệt lịch' },
  { value: 'SUBMIT_LEAVE', label: 'Gửi nghỉ phép' },
  { value: 'REJECT_SWAP', label: 'Từ chối đổi ca' },
  { value: 'SUBMIT_PREFERENCE', label: 'Gửi nguyện vọng' },
]

export function ActivityLogsPage() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('ALL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState<PaginatedResult<ActivityLog>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })

  const fetchData = async () => {
    setLoading(true)
    setError('')

    try {
      const result = await activityLogService.list({ page, pageSize: 5, search, action, startDate, endDate })
      setData(result)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Không thể tải nhật ký hoạt động.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    const load = async () => {
      setLoading(true)
      setError('')

      try {
        const result = await activityLogService.list({ page, pageSize: 5, search, action, startDate, endDate })
        if (mounted) {
          setData(result)
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : 'Không thể tải nhật ký hoạt động.')
        }
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void load()
    return () => {
      mounted = false
    }
  }, [page, search, action, startDate, endDate])

  return (
    <div className="space-y-6">
      <PageHeader title="Nhật ký hoạt động" description="Xem nhật ký thao tác của nhân viên và quản lý trong các hoạt động liên quan tới lịch làm việc." />

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 md:grid-cols-[1.4fr_1fr_0.8fr_0.8fr]">
            <SearchInput value={search} placeholder="Tìm theo người thực hiện hoặc đối tượng" onChange={setSearch} />
            <select value={action} onChange={(event) => { setAction(event.target.value); setPage(1) }} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm">
              {actionOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
            <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
            <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
          </div>

          {loading ? (
            <LoadingState label="Đang tải nhật ký hoạt động..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchData} />
          ) : data.data.length === 0 ? (
            <EmptyState title="Không có nhật ký" description="Chưa có dữ liệu hoạt động phù hợp với bộ lọc." />
          ) : (
            <>
              <DataTable
                data={data.data}
                columns={[
                  { key: 'timestamp', label: 'Thời gian', render: (item: ActivityLog) => <span>{new Date(item.timestamp).toLocaleString('vi-VN')}</span> },
                  { key: 'actorName', label: 'Người thực hiện' },
                  { key: 'actorRole', label: 'Vai trò', render: (item: ActivityLog) => <span>{item.actorRole === 'MANAGER' ? 'Quản lý' : 'Nhân viên'}</span> },
                  { key: 'action', label: 'Hành động' },
                  { key: 'target', label: 'Đối tượng' },
                  { key: 'description', label: 'Mô tả' },
                  { key: 'status', label: 'Trạng thái', render: (item: ActivityLog) => <span className={`rounded-full px-2 py-1 text-xs font-medium ${item.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-700' : item.status === 'FAILED' ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-700'}`}>{item.status}</span> },
                  { key: 'ipAddress', label: 'IP' },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button variant="outline" onClick={() => setSearch('')}><Search className="mr-2 h-4 w-4" />Xóa bộ lọc</Button>
      </div>
    </div>
  )
}

import { Home, TriangleAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-lg">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-amber-100 text-amber-600">
          <TriangleAlert className="h-8 w-8" />
        </div>
        <h1 className="mt-5 text-4xl font-bold text-slate-900">404</h1>
        <p className="mt-3 text-xl font-semibold text-slate-800">Trang không tồn tại</p>
        <p className="mt-2 text-sm text-slate-500">Đường dẫn bạn truy cập chưa được hỗ trợ hoặc đã bị đổi tên.</p>

        <Link to="/" className="mt-6 inline-flex">
          <Button className="gap-2">
            <Home className="h-4 w-4" />
            Quay về trang chủ
          </Button>
        </Link>
      </div>
    </div>
  )
}

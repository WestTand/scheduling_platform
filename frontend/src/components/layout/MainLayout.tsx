import {
  Bell,
  ChevronDown,
  Menu,
  Search,
  ShieldCheck,
} from 'lucide-react'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Sidebar } from '@/components/layout/Sidebar'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/contexts/useAuth'
import { navItems } from '@/mocks/dashboardData'
import type { User } from '@/types/auth'

const breadcrumbLabels: Record<string, string> = {
  manager: 'Quản lý',
  employee: 'Nhân viên',
  dashboard: 'Tổng quan',
  employees: 'Nhân viên',
  'shift-types': 'Loại ca trực',
  'leave-requests': 'Yêu cầu ngày nghỉ',
  preferences: 'Nguyện vọng làm việc',
  'schedule-generation': 'Tạo lịch tự động',
  schedules: 'Quản lý lịch',
}

interface MainLayoutProps {
  user: User
  title: string
  onLogout?: () => void
}

export function MainLayout({ user, title, onLogout }: MainLayoutProps) {
  const { logout } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const location = useLocation()

  const handleLogout = () => {
    setShowConfirm(false)
    logout()
    onLogout?.()
  }

  const breadcrumb = location.pathname
    .split('/')
    .filter(Boolean)
    .map((segment) => breadcrumbLabels[segment] ?? segment.replace(/-/g, ' '))
    .join(' / ')

  const items = navItems[user.role]

  return (
    <div className="flex min-h-screen bg-slate-100 text-slate-900">
      <Sidebar
        items={items}
        user={user}
        isMobileOpen={mobileOpen}
        onMobileToggle={() => setMobileOpen((current) => !current)}
        onLogout={() => setShowConfirm(true)}
      />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-slate-200 bg-white/90 backdrop-blur-sm">
          <div className="flex items-center justify-between gap-4 px-4 py-3 sm:px-6">
            <div className="flex items-center gap-3">
              <Button
                variant="outline"
                size="icon"
                className="lg:hidden"
                onClick={() => setMobileOpen((current) => !current)}
                aria-label="Mở menu"
              >
                <Menu className="h-4 w-4" />
              </Button>
              <div>
                <p className="text-xs uppercase tracking-[0.2em] text-slate-500">Hệ thống</p>
                <h1 className="text-lg font-semibold text-slate-900">{title}</h1>
              </div>
            </div>

            <div className="flex items-center gap-2 sm:gap-3">
              <div className="hidden items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500 md:flex">
                <Search className="h-4 w-4" />
                <span>Tìm kiếm</span>
              </div>
              <Button variant="ghost" size="icon" aria-label="Thông báo">
                <Bell className="h-4 w-4" />
              </Button>
              <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-2 py-1.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-blue-100 text-sm font-semibold text-blue-700">
                  {user.avatar ?? user.name.slice(0, 2).toUpperCase()}
                </div>
                <div className="hidden sm:block">
                  <p className="text-sm font-medium text-slate-900">{user.name}</p>
                  <p className="text-xs text-slate-500">{user.role === 'MANAGER' ? 'Quản lý' : 'Nhân viên'}</p>
                </div>
                <ChevronDown className="h-4 w-4 text-slate-500" />
              </div>
            </div>
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6">
          <div className="mb-6 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <span className="font-medium text-slate-700">Trang</span>
              <span>/</span>
              <span className="capitalize">{breadcrumb || 'dashboard'}</span>
            </div>
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700">
              <ShieldCheck className="h-3.5 w-3.5" />
              {user.role === 'MANAGER' ? 'Quản lý' : 'Nhân viên'} hoạt động
            </div>
          </div>

          <Outlet />
        </main>
      </div>

      <ConfirmDialog
        open={showConfirm}
        title="Đăng xuất"
        description="Bạn có chắc chắn muốn đăng xuất không?"
        cancelLabel="Hủy"
        confirmLabel="Đăng xuất"
        onCancel={() => setShowConfirm(false)}
        onConfirm={handleLogout}
      />
    </div>
  )
}

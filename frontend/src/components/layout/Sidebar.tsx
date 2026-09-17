import {
  AlertCircle,
  Bell,
  CalendarDays,
  CalendarRange,
  CalendarX2,
  ClipboardList,
  FileClock,
  LayoutDashboard,
  LogOut,
  Menu,
  Sparkles,
  Users,
  ArrowLeftRight,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { leaveRequestSeed, notificationSeed, preferenceSeed, swapRequestSeed } from '@/mocks/managementData'
import { scheduleSeed } from '@/mocks/schedules'
import { Role, type User } from '@/types/auth'

const iconMap: Record<string, LucideIcon> = {
  LayoutDashboard,
  Users,
  CalendarRange,
  CalendarX2,
  ClipboardList,
  Sparkles,
  CalendarDays,
  ArrowLeftRight,
  Bell,
  FileClock,
  AlertCircle,
}

interface SidebarItem {
  label: string
  href: string
  icon: string
}

const getBadgeCount = (label: string, role: Role) => {
  if (label === 'Ngày nghỉ') {
    return leaveRequestSeed.filter((item) => item.status === 'PENDING').length
  }
  if (label === 'Nguyện vọng') {
    return preferenceSeed.filter((item) => item.status === 'PENDING').length
  }
  if (label === 'Yêu cầu đổi ca') {
    return swapRequestSeed.filter((item) => item.status === 'PENDING').length
  }
  if (label === 'Thông báo') {
    return notificationSeed.filter((item) => !item.isRead).length
  }
  if (role === Role.MANAGER && label === 'Quản lý lịch') {
    return scheduleSeed.filter((item) => item.status === 'PENDING_APPROVAL').length
  }
  return 0
}

interface SidebarProps {
  items: SidebarItem[]
  user: User
  isMobileOpen: boolean
  onMobileToggle: () => void
  onLogout: () => void
}

export function Sidebar({ items, user, isMobileOpen, onMobileToggle, onLogout }: SidebarProps) {
  const navContent = (
    <aside className="flex h-full flex-col border-r border-slate-200 bg-slate-50/80 backdrop-blur-sm">
      <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-sm font-bold text-white">
            {user.name
              .split(' ')
              .map((part) => part[0])
              .slice(0, 2)
              .join('')}
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-900">{user.name}</p>
            <p className="text-xs text-slate-500">{user.role === Role.MANAGER ? 'Quản lý' : 'Nhân viên'}</p>
          </div>
        </div>
        <div className="lg:hidden">
          <Button variant="ghost" size="icon" onClick={onMobileToggle} aria-label="Đóng menu">
            <Menu className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {items.map((item) => {
          const Icon = iconMap[item.icon] ?? LayoutDashboard

          return (
            <NavLink
              key={item.href}
              to={item.href}
              end={item.href.endsWith('/dashboard')}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-600 hover:bg-slate-200 hover:text-slate-900'
                }`
              }
            >
              <Icon className="h-4 w-4" />
              <span className="min-w-0 flex-1 truncate">{item.label}</span>
              {getBadgeCount(item.label, user.role) > 0 && <span className="min-w-5 rounded-full bg-amber-100 px-1.5 py-0.5 text-center text-[11px] font-semibold text-amber-700">{getBadgeCount(item.label, user.role)}</span>}
            </NavLink>
          )
        })}
      </nav>

      <div className="border-t border-slate-200 p-3">
        <Button variant="outline" className="w-full justify-start gap-2" onClick={onLogout}>
          <LogOut className="h-4 w-4" />
          Đăng xuất
        </Button>
      </div>
    </aside>
  )

  return (
    <>
      <div className="hidden h-screen lg:block lg:w-72">{navContent}</div>
      {isMobileOpen && (
        <div className="fixed inset-0 z-40 bg-slate-900/30 lg:hidden">
          <div className="h-full w-72 max-w-[85vw]">{navContent}</div>
        </div>
      )}
    </>
  )
}

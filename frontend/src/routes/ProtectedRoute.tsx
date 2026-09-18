import type { PropsWithChildren } from 'react'
import { Navigate } from 'react-router-dom'

import { useAuth } from '@/contexts/useAuth'
import { Role } from '@/types/auth'

interface ProtectedRouteProps extends PropsWithChildren {
  requiredRole?: Role
}

export function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { user, isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100">
        <div className="rounded-xl border bg-white px-6 py-4 text-sm font-medium text-slate-700 shadow-sm">
          Đang kiểm tra phiên đăng nhập...
        </div>
      </div>
    )
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />
  }

  if (requiredRole && user.role !== requiredRole) {
    const redirectTo = user.role === Role.MANAGER ? '/manager/dashboard' : '/employee/dashboard'
    return <Navigate to={redirectTo} replace />
  }

  return <>{children}</>
}

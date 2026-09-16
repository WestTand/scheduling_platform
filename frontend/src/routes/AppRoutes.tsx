import { Navigate, Route, Routes } from 'react-router-dom'

import { MainLayout } from '@/components/layout/MainLayout'
import { AuthProvider } from '@/contexts/AuthContext'
import { useAuth } from '@/contexts/useAuth'
import { Role, type User } from '@/types/auth'
import { LoginPage } from '@/pages/auth/LoginPage'
import { EmployeeDashboardPage } from '@/pages/employee/EmployeeDashboardPage'
import { EmployeesPage } from '@/pages/manager/EmployeesPage'
import { LeaveRequestsPage } from '@/pages/manager/LeaveRequestsPage'
import { ManagerDashboardPage } from '@/pages/manager/ManagerDashboardPage'
import { ShiftTypesPage } from '@/pages/manager/ShiftTypesPage'
import { WorkPreferencesPage } from '@/pages/manager/WorkPreferencesPage'
import { ScheduleDetailPage } from '@/pages/manager/ScheduleDetailPage'
import { ScheduleGenerationPage } from '@/pages/manager/ScheduleGenerationPage'
import { SchedulesPage } from '@/pages/manager/SchedulesPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ProtectedRoute } from '@/routes/ProtectedRoute'

const redirectToRoleDashboard = (user: User | null) => {
  if (!user) {
    return '/login'
  }

  return user.role === Role.MANAGER ? '/manager/dashboard' : '/employee/dashboard'
}

function AppShell() {
  const { user, isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route path="/" element={<Navigate to={redirectToRoleDashboard(user)} replace />} />
      <Route
        path="/login"
        element={
          isAuthenticated && user ? <Navigate to={redirectToRoleDashboard(user)} replace /> : <LoginPage />
        }
      />

      <Route
        element={
          <ProtectedRoute>
            <MainLayout
              user={user!}
              title={user?.role === Role.MANAGER ? 'Bảng điều khiển quản lý' : 'Bảng điều khiển nhân viên'}
              onLogout={() => {}}
            />
          </ProtectedRoute>
        }
      >
        <Route
          path="/manager/dashboard"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ManagerDashboardPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/dashboard"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeeDashboardPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/employees"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <EmployeesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/shift-types"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ShiftTypesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/leave-requests"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <LeaveRequestsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/leaves"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <Navigate to="/manager/leave-requests" replace />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/preferences"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <WorkPreferencesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/schedule-generation"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ScheduleGenerationPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/schedules"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <SchedulesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/schedules/:scheduleId"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ScheduleDetailPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/*"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <NotFoundPage />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export function AppRoutes() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  )
}

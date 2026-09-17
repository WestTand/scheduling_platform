import { Navigate, Route, Routes } from 'react-router-dom'

import { MainLayout } from '@/components/layout/MainLayout'
import { AuthProvider } from '@/contexts/AuthContext'
import { useAuth } from '@/contexts/useAuth'
import { Role, type User } from '@/types/auth'
import { LoginPage } from '@/pages/auth/LoginPage'
import { EmployeeDashboardPage } from '@/pages/employee/EmployeeDashboardPage'
import { EmployeeLeaveRequestsPage } from '@/pages/employee/EmployeeLeaveRequestsPage'
import { EmployeeNotificationsPage } from '@/pages/employee/EmployeeNotificationsPage'
import { EmployeePreferencesPage } from '@/pages/employee/EmployeePreferencesPage'
import { EmployeeSchedulePage } from '@/pages/employee/EmployeeSchedulePage'
import { EmployeeSwapRequestsPage } from '@/pages/employee/EmployeeSwapRequestsPage'
import { ActivityLogsPage } from '@/pages/manager/ActivityLogsPage'
import { EmployeesPage } from '@/pages/manager/EmployeesPage'
import { ExportsPage } from '@/pages/manager/ExportsPage'
import { LeaveRequestsPage } from '@/pages/manager/LeaveRequestsPage'
import { ManagerDashboardPage } from '@/pages/manager/ManagerDashboardPage'
import { NotificationsPage } from '@/pages/manager/NotificationsPage'
import { ScheduleDetailPage } from '@/pages/manager/ScheduleDetailPage'
import { ScheduleEditPage } from '@/pages/manager/ScheduleEditPage'
import { ScheduleGenerationPage } from '@/pages/manager/ScheduleGenerationPage'
import { SchedulesPage } from '@/pages/manager/SchedulesPage'
import { ShiftTypesPage } from '@/pages/manager/ShiftTypesPage'
import { SwapRequestsPage } from '@/pages/manager/SwapRequestsPage'
import { WorkPreferencesPage } from '@/pages/manager/WorkPreferencesPage'
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
          path="/manager/schedules/:scheduleId/edit"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ScheduleEditPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/swap-requests"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <SwapRequestsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/exports"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ExportsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/notifications"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <NotificationsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/manager/activity-logs"
          element={
            <ProtectedRoute requiredRole={Role.MANAGER}>
              <ActivityLogsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/schedule"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeeSchedulePage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/leave-requests"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeeLeaveRequestsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/leaves"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <Navigate to="/employee/leave-requests" replace />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/preferences"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeePreferencesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/swap-requests"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeeSwapRequestsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/shift-swaps"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <Navigate to="/employee/swap-requests" replace />
            </ProtectedRoute>
          }
        />

        <Route
          path="/employee/notifications"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
              <EmployeeNotificationsPage />
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

        <Route
          path="/employee/*"
          element={
            <ProtectedRoute requiredRole={Role.EMPLOYEE}>
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

import { Role } from '@/types/auth'

export const currentDateLabel = '16/09/2026'

export const managerDashboardData = {
  summary: [
    { label: 'Tổng nhân viên', value: '126', trend: '+8% so với tuần trước', tone: 'blue' },
    { label: 'Ca trực tuần này', value: '38', trend: '12 ca cần cân bằng', tone: 'amber' },
    { label: 'Yêu cầu chờ duyệt', value: '14', trend: '4 đổi ca, 10 nghỉ phép', tone: 'green' },
    { label: 'Cảnh báo xung đột', value: '3', trend: '2 thiếu nhân sự', tone: 'red' },
  ],
  requests: [
    { id: 'REQ-101', type: 'Đổi ca', employee: 'Lê Thị Hòa', shift: 'Ca chiều', date: '17/09', status: 'Chờ duyệt' },
    { id: 'REQ-102', type: 'Nghỉ phép', employee: 'Hoàng Văn Tâm', shift: 'Cả ngày', date: '18/09', status: 'Chờ duyệt' },
    { id: 'REQ-103', type: 'Yêu cầu thay ca', employee: 'Phạm Minh Khoa', shift: 'Ca đêm', date: '19/09', status: 'Chờ duyệt' },
  ],
  alerts: [
    { title: 'Thiếu nhân sự', detail: 'Ca sáng ngày 18/09 thiếu 2 nhân viên tại khu vực A', severity: 'Cao' },
    { title: 'Trùng lịch', detail: '2 yêu cầu đổi ca cùng thời điểm về ca chiều', severity: 'Trung bình' },
    { title: 'Nghỉ không hợp lệ', detail: '1 nhân viên đã đăng ký 3 ngày nghỉ liền trong tuần', severity: 'Thấp' },
  ],
}

export const employeeDashboardData = {
  summary: [
    { label: 'Ca làm hôm nay', value: 'Ca sáng', detail: '07:00 - 12:00' },
    { label: 'Ca làm tiếp theo', value: 'Ca chiều', detail: '13:00 - 18:00' },
    { label: 'Tổng giờ làm', value: '42h', detail: 'Tuần này' },
    { label: 'Yêu cầu chờ xử lý', value: '2', detail: '1 đổi ca, 1 nghỉ phép' },
  ],
  nextShifts: [
    { day: 'Thứ 5', date: '17/09', shift: 'Ca sáng', hours: '07:00 - 12:00' },
    { day: 'Thứ 6', date: '18/09', shift: 'Ca chiều', hours: '13:00 - 18:00' },
    { day: 'Thứ 7', date: '19/09', shift: 'Ca đêm', hours: '19:00 - 23:00' },
  ],
  notifications: [
    'Lịch làm việc tuần tới đã được cập nhật.',
    'Bạn có 1 yêu cầu đổi ca đang chờ duyệt.',
    'Thông báo về quy định nghỉ phép mới đã được gửi.'
  ],
}

export const navItems = {
  [Role.MANAGER]: [
    { label: 'Tổng quan', href: '/manager/dashboard', icon: 'LayoutDashboard' },
    { label: 'Nhân viên', href: '/manager/employees', icon: 'Users' },
    { label: 'Loại ca trực', href: '/manager/shift-types', icon: 'CalendarRange' },
    { label: 'Ngày nghỉ', href: '/manager/leave-requests', icon: 'CalendarX2' },
    { label: 'Nguyện vọng', href: '/manager/preferences', icon: 'ClipboardList' },
    { label: 'Tạo lịch tự động', href: '/manager/schedule-generation', icon: 'Sparkles' },
    { label: 'Quản lý lịch', href: '/manager/schedules', icon: 'CalendarDays' },
    { label: 'Yêu cầu đổi ca', href: '/manager/swap-requests', icon: 'ArrowLeftRight' },
    { label: 'Xuất dữ liệu', href: '/manager/exports', icon: 'FileClock' },
    { label: 'Thông báo', href: '/manager/notifications', icon: 'Bell' },
    { label: 'Nhật ký hoạt động', href: '/manager/activity-logs', icon: 'FileClock' },
  ],
  [Role.EMPLOYEE]: [
    { label: 'Tổng quan', href: '/employee/dashboard', icon: 'LayoutDashboard' },
    { label: 'Lịch làm việc', href: '/employee/schedule', icon: 'CalendarDays' },
    { label: 'Đăng ký ngày nghỉ', href: '/employee/leave-requests', icon: 'CalendarX2' },
    { label: 'Đăng ký nguyện vọng', href: '/employee/preferences', icon: 'ClipboardList' },
    { label: 'Yêu cầu đổi ca', href: '/employee/swap-requests', icon: 'ArrowLeftRight' },
    { label: 'Thông báo', href: '/employee/notifications', icon: 'Bell' },
  ],
}

import type { ScheduleDetail, WorkSchedule } from '@/types/schedule'
import { ScheduleStatus } from '@/types/schedule'

export const scheduleSeed: WorkSchedule[] = [
  {
    id: 'schedule-demo-01',
    name: 'Lịch vận hành tuần 3 tháng 09',
    startDate: '2026-09-14',
    endDate: '2026-09-20',
    createdBy: 'Nguyễn Thị Lan',
    createdAt: '2026-09-13T16:20:00',
    employeeCount: 12,
    assignmentCount: 38,
    shiftCount: 3,
    status: ScheduleStatus.PUBLISHED,
    isMock: true,
  },
  {
    id: 'schedule-demo-02',
    name: 'Lịch dự kiến tuần 4 tháng 09',
    startDate: '2026-09-21',
    endDate: '2026-09-27',
    createdBy: 'Nguyễn Thị Lan',
    createdAt: '2026-09-16T09:10:00',
    employeeCount: 12,
    assignmentCount: 0,
    shiftCount: 3,
    status: ScheduleStatus.DRAFT,
    isMock: true,
  },
]

export const scheduleDetailSeed: ScheduleDetail = {
  ...scheduleSeed[0],
  assignments: [
    { id: 'assignment-01', scheduleId: 'schedule-demo-01', date: '2026-09-14', shiftCode: 'DAY', shiftName: 'Ca ngày', startTime: '07:00', endTime: '12:00', color: '#2563eb', employeeNames: ['Trần Văn Nam', 'Lê Thị Hòa'], warningCount: 0 },
    { id: 'assignment-02', scheduleId: 'schedule-demo-01', date: '2026-09-14', shiftCode: 'EVENING', shiftName: 'Ca chiều', startTime: '13:00', endTime: '18:00', color: '#f59e0b', employeeNames: ['Hoàng Văn Tâm', 'Phạm Minh Khoa'], warningCount: 1 },
    { id: 'assignment-03', scheduleId: 'schedule-demo-01', date: '2026-09-15', shiftCode: 'NIGHT', shiftName: 'Ca đêm', startTime: '19:00', endTime: '23:00', color: '#8b5cf6', employeeNames: ['Lê Thị Hòa'], warningCount: 1 },
    { id: 'assignment-04', scheduleId: 'schedule-demo-01', date: '2026-09-16', shiftCode: 'DAY', shiftName: 'Ca ngày', startTime: '07:00', endTime: '12:00', color: '#2563eb', employeeNames: ['Trần Văn Nam', 'Hoàng Văn Tâm'], warningCount: 0 },
    { id: 'assignment-05', scheduleId: 'schedule-demo-01', date: '2026-09-17', shiftCode: 'EVENING', shiftName: 'Ca chiều', startTime: '13:00', endTime: '18:00', color: '#f59e0b', employeeNames: ['Phạm Minh Khoa', 'Trần Văn Nam'], warningCount: 0 },
    { id: 'assignment-06', scheduleId: 'schedule-demo-01', date: '2026-09-18', shiftCode: 'DAY', shiftName: 'Ca ngày', startTime: '07:00', endTime: '12:00', color: '#2563eb', employeeNames: ['Lê Thị Hòa', 'Hoàng Văn Tâm'], warningCount: 1 },
  ],
  warnings: [
    { id: 'warning-01', type: 'HARD_CONSTRAINT', severity: 'HIGH', employeeName: 'Lê Thị Hòa', date: '15/09/2026', shift: 'Ca đêm', content: 'Khoảng nghỉ giữa hai ca chưa đủ theo dữ liệu minh họa.', suggestion: 'Xem xét đổi ca hoặc bố trí nhân viên khác.' },
    { id: 'warning-02', type: 'SOFT_CONSTRAINT', severity: 'MEDIUM', employeeName: 'Phạm Minh Khoa', date: '14/09/2026', shift: 'Ca chiều', content: 'Nhân viên có nhiều ca cuối tuần trong lịch mẫu.', suggestion: 'Cân nhắc phân bổ lại khi dịch vụ lập lịch được tích hợp.' },
    { id: 'warning-03', type: 'SOFT_CONSTRAINT', severity: 'MEDIUM', employeeName: 'Trần Văn Nam', date: '18/09/2026', shift: 'Ca ngày', content: 'Nguyện vọng ca sáng chưa được đáp ứng trong dữ liệu mẫu.', suggestion: 'Đối chiếu nguyện vọng trước khi duyệt lịch thực tế.' },
    { id: 'warning-04', type: 'INFO', severity: 'LOW', employeeName: 'Hoàng Văn Tâm', date: 'Tuần 3', shift: 'Tổng hợp', content: 'Có chênh lệch tổng giờ làm giữa các nhân viên.', suggestion: 'Dùng kết quả đánh giá từ dịch vụ lập lịch để xem xét.' },
  ],
  evaluation: {
    totalPenalty: 18.4,
    fairnessPenalty: 4.2,
    consecutiveShiftPenalty: 3.1,
    weekendPenalty: 2.8,
    preferencePenalty: 5.5,
    workHourImbalance: 2.8,
    shiftCountGap: 2,
    workHourGap: 4.5,
    fulfilledPreferences: 8,
    weights: { fairness: 5, consecutiveShift: 4, weekend: 3, preference: 6, workHour: 4 },
  },
}

export const scheduleStatusLabels: Record<WorkSchedule['status'], string> = {
  DRAFT: 'Bản nháp',
  PENDING_APPROVAL: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  PUBLISHED: 'Đã công bố',
}

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
    assignmentCount: 41,
    shiftCount: 6,
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
    shiftCount: 6,
    status: ScheduleStatus.DRAFT,
    isMock: true,
  },
]

export const scheduleDetailSeed: ScheduleDetail = {
  ...scheduleSeed[0],
  employeePool: ['Trần Văn Nam', 'Lê Thị Hòa', 'Hoàng Văn Tâm', 'Phạm Minh Khoa'],
  employeePoolIds: [],
  assignments: [
    { id: 'assignment-01', scheduleId: 'schedule-demo-01', date: '2026-09-14', shiftCode: 'THONG_TAM', employeeNames: ['Trần Văn Nam', 'Lê Thị Hòa'], warningCount: 0 },
    { id: 'assignment-02', scheduleId: 'schedule-demo-01', date: '2026-09-14', shiftCode: 'NGOAI_GIO_1', employeeNames: ['Hoàng Văn Tâm', 'Phạm Minh Khoa'], warningCount: 1 },
    { id: 'assignment-03', scheduleId: 'schedule-demo-01', date: '2026-09-15', shiftCode: 'NGOAI_GIO_2', employeeNames: ['Lê Thị Hòa'], warningCount: 1 },
    { id: 'assignment-04', scheduleId: 'schedule-demo-01', date: '2026-09-16', shiftCode: 'THONG_TAM', employeeNames: ['Trần Văn Nam', 'Hoàng Văn Tâm'], warningCount: 0 },
    { id: 'assignment-05', scheduleId: 'schedule-demo-01', date: '2026-09-17', shiftCode: 'NGOAI_GIO_1', employeeNames: ['Phạm Minh Khoa', 'Trần Văn Nam'], warningCount: 0 },
    { id: 'assignment-06', scheduleId: 'schedule-demo-01', date: '2026-09-18', shiftCode: 'NGOAI_GIO_2', employeeNames: ['Lê Thị Hòa', 'Hoàng Văn Tâm'], warningCount: 1 },
    { id: 'assignment-07', scheduleId: 'schedule-demo-01', date: '2026-09-19', shiftCode: 'TRUC_DEM', employeeNames: ['Trần Văn Nam'], warningCount: 0 },
    { id: 'assignment-08', scheduleId: 'schedule-demo-01', date: '2026-09-19', shiftCode: 'TRUC_SANG', employeeNames: ['Lê Thị Hòa'], warningCount: 0 },
    { id: 'assignment-09', scheduleId: 'schedule-demo-01', date: '2026-09-19', shiftCode: 'TRUC_CHIEU', employeeNames: ['Hoàng Văn Tâm'], warningCount: 0 },
  ],
  warnings: [
    { id: 'warning-01', type: 'HARD_CONSTRAINT', severity: 'HIGH', employeeName: 'Lê Thị Hòa', date: '15/09/2026', shiftCode: 'NGOAI_GIO_2', content: 'Khoảng nghỉ giữa hai ca chưa đủ theo dữ liệu minh họa.', suggestion: 'Xem xét đổi ca hoặc bố trí nhân viên khác.' },
    { id: 'warning-02', type: 'SOFT_CONSTRAINT', severity: 'MEDIUM', employeeName: 'Phạm Minh Khoa', date: '14/09/2026', shiftCode: 'NGOAI_GIO_1', content: 'Nhân viên có nhiều ca ngoài giờ trong lịch mẫu.', suggestion: 'Cân nhắc phân bổ lại khi dịch vụ lập lịch được tích hợp.' },
    { id: 'warning-03', type: 'SOFT_CONSTRAINT', severity: 'MEDIUM', employeeName: 'Trần Văn Nam', date: '18/09/2026', shiftCode: 'NGOAI_GIO_2', content: 'Nguyện vọng ca thông tầm chưa được đáp ứng trong dữ liệu mẫu.', suggestion: 'Đối chiếu nguyện vọng trước khi duyệt lịch thực tế.' },
    { id: 'warning-04', type: 'INFO', severity: 'LOW', employeeName: 'Hoàng Văn Tâm', date: 'Tuần 3', shiftCode: '', content: 'Có chênh lệch phân công giữa các nhân viên theo dữ liệu minh họa.', suggestion: 'Đối chiếu cảnh báo do dịch vụ lập lịch trả về.' },
  ],
}

export const scheduleStatusLabels: Record<WorkSchedule['status'], string> = {
  DRAFT: 'Bản nháp',
  PENDING_APPROVAL: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  PUBLISHED: 'Đã công bố',
  CANCELLED: 'Đã hủy',
}

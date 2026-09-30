import type { ShiftCode } from '@/configs/shiftDefinitions'
import type { BackendOptimizationDiagnostic, BackendPeriodScheduleResponse, BackendScheduleEmployeePoolItem, BackendSchedulePeriodResponse, BackendScheduledShiftResponse, BackendShiftAssignmentResponse } from '@/types/backendSchedule'
import { ScheduleStatus, type ScheduleAssignment, type ScheduleDetail, type ScheduleWarning, type WorkSchedule } from '@/types/schedule'

const normalizeStatus = (status: string) => Object.values(ScheduleStatus).includes(status as ScheduleStatus) ? status as ScheduleStatus : ScheduleStatus.DRAFT

const shiftNameToCode = (shiftName: string): ShiftCode => {
  const normalized = shiftName.trim().toLowerCase()
  if (normalized === 'thong tam') return 'THONG_TAM'
  if (normalized === 'ngoai gio 1') return 'NGOAI_GIO_1'
  if (normalized === 'ngoai gio 2') return 'NGOAI_GIO_2'
  if (normalized === 'truc sang') return 'TRUC_SANG'
  if (normalized === 'truc chieu') return 'TRUC_CHIEU'
  if (normalized === 'truc dem') return 'TRUC_DEM'
  return 'THONG_TAM'
}

export const adaptBackendSchedulePeriod = (period: BackendSchedulePeriodResponse): WorkSchedule => ({
  id: String(period.id),
  name: period.periodName,
  startDate: period.startDate,
  endDate: period.endDate,
  createdBy: period.createdByUsername ?? 'Không rõ',
  createdAt: period.createdAt ?? period.publishedAt ?? '',
  employeeCount: 0,
  assignmentCount: 0,
  shiftCount: 0,
  status: normalizeStatus(period.status),
  isMock: false,
  optimizationStatus: period.optimizationStatus ?? null,
})

const formatWarningDate = (date: string) => {
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

const shortageFor = (shift: BackendScheduledShiftResponse) => Math.max(0, shift.requiredStaff - shift.assignments.length)

const diagnosticCountForShift = (shiftId: number, diagnostics: BackendOptimizationDiagnostic[]) => diagnostics.filter((diagnostic) => diagnostic.workShiftId === shiftId).length

const shiftWarningCount = (shift: BackendScheduledShiftResponse, diagnostics: BackendOptimizationDiagnostic[] = []) => (shortageFor(shift) > 0 ? 1 : 0) + diagnosticCountForShift(shift.id, diagnostics)

const adaptAssignment = (assignment: BackendShiftAssignmentResponse, shift: BackendScheduledShiftResponse, diagnostics: BackendOptimizationDiagnostic[]): ScheduleAssignment => ({
  id: String(assignment.id),
  scheduleId: String(assignment.periodId),
  workShiftId: String(assignment.workShiftId),
  employeeId: String(assignment.employeeId),
  date: shift.workDate,
  shiftCode: shiftNameToCode(shift.shiftName),
  employeeNames: [assignment.fullName],
  warningCount: shiftWarningCount(shift, diagnostics),
  locked: assignment.locked,
})

const adaptUnassignedShift = (shift: BackendScheduledShiftResponse, periodId: number, diagnostics: BackendOptimizationDiagnostic[]): ScheduleAssignment => ({
  id: `work-shift-${shift.id}`,
  scheduleId: String(periodId),
  workShiftId: String(shift.id),
  date: shift.workDate,
  shiftCode: shiftNameToCode(shift.shiftName),
  employeeNames: [],
  warningCount: shiftWarningCount(shift, diagnostics),
  locked: false,
})

const buildStaffingWarnings = (schedule: BackendPeriodScheduleResponse): ScheduleWarning[] => schedule.workShifts
  .filter((shift) => shortageFor(shift) > 0)
  .map((shift) => {
    const shiftCode = shiftNameToCode(shift.shiftName)
    const assignedCount = shift.assignments.length
    const missingCount = shortageFor(shift)
    const poolNames = schedule.employeePool?.map((employee) => employee.fullName) ?? []
    return {
      id: `understaffed-shift-${shift.id}`,
      type: 'HARD_CONSTRAINT',
      severity: 'HIGH',
      employeeName: poolNames.length ? `Pool: ${poolNames.join(', ')}` : 'Pool nhân viên chưa ghi nhận',
      date: shift.workDate,
      shiftCode,
      content: `Ca ${shift.shiftName} ngày ${formatWarningDate(shift.workDate)} thiếu ${missingCount} nhân viên (cần ${shift.requiredStaff}, hiện có ${assignedCount}).`,
      suggestion: 'Chọn thêm nhân viên phù hợp trong pool, giảm định mức nhân sự của ca, bỏ bớt loại ca/ngày, hoặc vào trang Chỉnh sửa để phân công thủ công.',
    } satisfies ScheduleWarning
  })

const buildDiagnosticWarnings = (schedule: BackendPeriodScheduleResponse): ScheduleWarning[] => {
  const latestRun = schedule.latestOptimizationRun
  if (!latestRun?.diagnostics?.length) return []
  const shiftById = new Map(schedule.workShifts.map((shift) => [shift.id, shift]))
  const employeeById = new Map((schedule.employeePool ?? []).map((employee: BackendScheduleEmployeePoolItem) => [employee.id, employee]))
  return latestRun.diagnostics.map((diagnostic, index) => {
    const shift = diagnostic.workShiftId == null ? undefined : shiftById.get(diagnostic.workShiftId)
    const employee = diagnostic.employeeId == null ? undefined : employeeById.get(diagnostic.employeeId)
    return {
      id: `optimization-diagnostic-${latestRun.runId}-${index}`,
      type: 'HARD_CONSTRAINT',
      severity: 'HIGH',
      employeeName: employee?.fullName ?? (diagnostic.employeeId == null ? 'Không áp dụng cho nhân viên cụ thể' : `Nhân viên #${diagnostic.employeeId}`),
      date: shift?.workDate ?? schedule.startDate,
      shiftCode: shift ? shiftNameToCode(shift.shiftName) : '',
      content: `${diagnostic.code}: ${diagnostic.message}`,
      suggestion: 'Đổi nhân viên, gỡ khóa phân công liên quan, hoặc chạy tối ưu lại sau khi chỉnh lịch.',
    } satisfies ScheduleWarning
  })
}

export const adaptBackendPeriodSchedule = (schedule: BackendPeriodScheduleResponse): ScheduleDetail => {
  const diagnostics = schedule.latestOptimizationRun?.diagnostics ?? []
  const assignments = schedule.workShifts.flatMap((shift) => shift.assignments.length ? shift.assignments.map((assignment) => adaptAssignment(assignment, shift, diagnostics)) : [adaptUnassignedShift(shift, schedule.periodId, diagnostics)])
  const assignedEmployeeNames = [...new Set(assignments.flatMap((assignment) => assignment.employeeNames))]
  const employeePool = schedule.employeePool?.length ? schedule.employeePool.map((employee) => employee.fullName) : assignedEmployeeNames
  const employeeCount = employeePool.length
  const assignmentCount = schedule.workShifts.reduce((total, shift) => total + shift.assignments.length, 0)
  return {
    id: String(schedule.periodId),
    name: schedule.periodName,
    startDate: schedule.startDate,
    endDate: schedule.endDate,
    createdBy: 'Backend',
    createdAt: '',
    employeeCount,
    assignmentCount,
    shiftCount: schedule.workShifts.length,
    status: normalizeStatus(schedule.status),
    isMock: false,
    optimizationStatus: schedule.latestOptimizationRun?.status ?? null,
    employeePool,
    assignments,
    warnings: [...buildStaffingWarnings(schedule), ...buildDiagnosticWarnings(schedule)],
  }
}

export const adaptBackendShiftAssignment = (assignment: BackendShiftAssignmentResponse): BackendShiftAssignmentResponse => assignment

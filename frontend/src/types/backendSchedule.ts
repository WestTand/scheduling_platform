export interface BackendSchedulePeriodResponse {
  id: number
  periodName: string
  startDate: string
  endDate: string
  versionNo: number
  status: string
  createdByAccountId: number | null
  createdByUsername: string | null
  approvedByAccountId: number | null
  approvedByUsername: string | null
  createdAt?: string | null
  publishedAt: string | null
  totalPenalty: number | null
  optimizationStatus?: string | null
}

export interface BackendShiftAssignmentResponse {
  id: number
  workShiftId: number
  periodId: number
  employeeId: number
  employeeCode: string
  fullName: string
  assignmentStatus: string
  assignedSource: string
  locked: boolean
}

export interface BackendScheduledShiftResponse {
  id: number
  workDate: string
  shiftTypeId: number
  shiftName: string
  shiftStartTime: string
  shiftEndTime: string
  startsAt: string
  endsAt: string
  workMinutes: number
  isTransitionShift: boolean
  overnight: boolean
  requiredStaff: number
  maxStaff: number
  assignments: BackendShiftAssignmentResponse[]
}

export interface BackendScheduleEmployeePoolItem {
  id: number
  employeeCode: string
  fullName: string
}

export interface BackendOptimizationDiagnostic {
  code: string
  message: string
  employeeId: number | null
  workShiftId: number | null
}

export interface BackendStaffingAdvice {
  requiredEmployees: number
  availableEmployees: number
  totalShifts: number
  exact: boolean
  reasons: string[]
}

export interface BackendLatestOptimizationRun {
  runId: number
  status: string
  totalPenalty: number | null
  penaltyDetailsJson: string | null
  errorMessage: string | null
  diagnostics: BackendOptimizationDiagnostic[]
  staffingAdvice?: BackendStaffingAdvice | null
}

export interface BackendPeriodScheduleResponse {
  periodId: number
  periodName: string
  status: string
  startDate: string
  endDate: string
  employeePool: BackendScheduleEmployeePoolItem[]
  latestOptimizationRun?: BackendLatestOptimizationRun | null
  workShifts: BackendScheduledShiftResponse[]
}

export interface BackendWorkShiftResponse {
  id: number
  periodId: number
  periodName: string
  shiftTypeId: number
  shiftName: string
  shiftStartTime: string
  shiftEndTime: string
  workDate: string
  startsAt: string
  endsAt: string
  workMinutes: number
  isTransitionShift: boolean
  requiredStaff: number
  note: string | null
}

export interface BackendOptimizeScheduleResponse {
  runId: number
  periodId: number
  aiStatus: string
  solverStatus: string | null
  totalPenalty: number | null
  assignmentCount: number | null
  warnings: string[]
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface BackendGenerateScheduleRequest {
  periodName: string
  startDate: string
  endDate: string
  versionNo: number
  employeeIds: number[]
  shiftTypeIds: number[]
}

export interface BackendGenerateScheduleResponse {
  periodId: number
  periodName: string
  status: string
  aiStatus: string
  solverStatus: string | null
  assignmentCount: number | null
  warnings: string[]
  errorMessage: string | null
}

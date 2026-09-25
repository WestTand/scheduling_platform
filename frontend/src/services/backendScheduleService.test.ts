import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendScheduleService } from '@/services/backendScheduleService'
import { AUTH_STORAGE_KEYS } from '@/services/authStorage'

const createStorage = (): Storage => {
  let values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear: () => { values = new Map() },
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key) },
    setItem: (key, value) => { values.set(key, value) },
  }
}

const jsonResponse = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
const noContentResponse = () => new Response(null, { status: 204 })

describe('backendScheduleService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('lists schedule periods with filters', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'DRAFT', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: null, approvedByUsername: null, publishedAt: null, totalPenalty: null },
    ])).mockResolvedValueOnce(jsonResponse({ periodId: 7, periodName: 'Tuần mẫu', status: 'DRAFT', startDate: '2026-09-21', endDate: '2026-09-27', workShifts: [] }))

    const result = await backendScheduleService.listPeriods({ search: 'mẫu', status: 'DRAFT', startDate: '2026-09-01', endDate: '2026-09-30' })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/admin/schedule-periods?status=DRAFT&from=2026-09-01&to=2026-09-30', expect.any(Object))
    expect(result).toHaveLength(1)
    expect(result[0]?.name).toBe('Tuần mẫu')
  })

  test('enriches listed periods with detail counts', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse([
        { id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'PUBLISHED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: null, approvedByUsername: null, publishedAt: null, totalPenalty: null },
      ]))
      .mockResolvedValueOnce(jsonResponse({
        periodId: 7,
        periodName: 'Tuần mẫu',
        status: 'PUBLISHED',
        startDate: '2026-09-21',
        endDate: '2026-09-27',
        employeePool: [],
        workShifts: [
          {
            id: 11,
            workDate: '2026-09-21',
            shiftTypeId: 100,
            shiftName: 'Thong tam',
            shiftStartTime: '08:00:00',
            shiftEndTime: '17:00:00',
            startsAt: '2026-09-21T08:00:00+07:00',
            endsAt: '2026-09-21T17:00:00+07:00',
            workMinutes: 480,
            isTransitionShift: false,
            overnight: false,
            requiredStaff: 2,
            maxStaff: 20,
            assignments: [
              { id: 21, workShiftId: 11, periodId: 7, employeeId: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: false },
              { id: 22, workShiftId: 11, periodId: 7, employeeId: 32, employeeCode: 'EMP002', fullName: 'Trần Thị Bình', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: false },
            ],
          },
        ],
      }))

    const result = await backendScheduleService.listPeriods({ status: 'PUBLISHED' })

    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/admin/schedule-periods/7/schedule', expect.any(Object))
    expect(result[0]).toMatchObject({ employeeCount: 2, assignmentCount: 2, shiftCount: 1 })
  })

  test('loads every listable backend status for all-status filter', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))

    await backendScheduleService.listPeriods({ status: 'ALL' })

    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/admin/schedule-periods?status=DRAFT', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/admin/schedule-periods?status=APPROVED', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/admin/schedule-periods?status=PUBLISHED', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(4, 'http://localhost:8080/api/admin/schedule-periods?status=CANCELLED', expect.any(Object))
  })

  test('returns empty list for pending approval without calling backend', async () => {
    const result = await backendScheduleService.listPeriods({ status: 'PENDING_APPROVAL' })

    expect(result).toEqual([])
    expect(fetch).not.toHaveBeenCalled()
  })

  test('loads period schedule detail', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ periodId: 7, periodName: 'Tuần mẫu', status: 'DRAFT', startDate: '2026-09-21', endDate: '2026-09-27', employeePool: [], workShifts: [] }))

    const result = await backendScheduleService.getScheduleDetail('7')

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/admin/schedule-periods/7/schedule', expect.any(Object))
    expect(result.id).toBe('7')
  })

  test('approves publishes and cancels schedule periods', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'APPROVED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: 1, approvedByUsername: 'manager@example.com', publishedAt: null, totalPenalty: null }))
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'PUBLISHED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: 1, approvedByUsername: 'manager@example.com', publishedAt: '2026-09-23T08:00:00', totalPenalty: null }))
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần mẫu', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'CANCELLED', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: null, approvedByUsername: null, publishedAt: null, totalPenalty: null }))

    const approved = await backendScheduleService.approvePeriod('7')
    const published = await backendScheduleService.publishPeriod('7')
    const cancelled = await backendScheduleService.cancelPeriod('7')

    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/admin/schedule-periods/7/approve', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/admin/schedule-periods/7/publish', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/admin/schedule-periods/7/cancel', expect.objectContaining({ method: 'PATCH' }))
    expect(approved.status).toBe('APPROVED')
    expect(published.status).toBe('PUBLISHED')
    expect(cancelled.status).toBe('CANCELLED')
  })

  test('mutates assignments and optimizes schedules', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ id: 7, periodName: 'Tuần tạo mới', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1, status: 'DRAFT', createdByAccountId: 1, createdByUsername: 'manager@example.com', approvedByAccountId: null, approvedByUsername: null, publishedAt: null, totalPenalty: null }))
      .mockResolvedValueOnce(jsonResponse({ id: 21, workShiftId: 11, periodId: 7, employeeId: 31, employeeCode: 'EMP001', fullName: 'Nguyễn Văn An', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: false }))
      .mockResolvedValueOnce(jsonResponse({ id: 21, workShiftId: 11, periodId: 7, employeeId: 32, employeeCode: 'EMP002', fullName: 'Trần Thị Bình', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: false }))
      .mockResolvedValueOnce(noContentResponse())
      .mockResolvedValueOnce(jsonResponse({ id: 21, workShiftId: 11, periodId: 7, employeeId: 32, employeeCode: 'EMP002', fullName: 'Trần Thị Bình', assignmentStatus: 'ASSIGNED', assignedSource: 'MANUAL', locked: true }))
      .mockResolvedValueOnce(jsonResponse({ runId: 99, periodId: 7, aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', totalPenalty: 0, assignmentCount: 4, warnings: [], errorMessage: null, startedAt: '2026-09-23T08:00:00', finishedAt: '2026-09-23T08:01:00' }))

    await backendScheduleService.createPeriod({ periodName: 'Tuần tạo mới', startDate: '2026-09-21', endDate: '2026-09-27', versionNo: 1 })
    await backendScheduleService.createAssignment('11', '31')
    await backendScheduleService.updateAssignment('21', '32')
    await backendScheduleService.deleteAssignment('21')
    await backendScheduleService.setAssignmentLocked('21', true)
    const result = await backendScheduleService.optimize('7')

    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/admin/schedule-periods', expect.objectContaining({ method: 'POST' }))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/admin/work-shifts/11/assignments', expect.objectContaining({ method: 'POST' }))
    expect(fetch).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/admin/assignments/21', expect.objectContaining({ method: 'PUT' }))
    expect(fetch).toHaveBeenNthCalledWith(4, 'http://localhost:8080/api/admin/assignments/21', expect.objectContaining({ method: 'DELETE' }))
    expect(fetch).toHaveBeenNthCalledWith(5, 'http://localhost:8080/api/admin/assignments/21/lock', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(6, 'http://localhost:8080/api/admin/schedule-periods/7/optimize', expect.objectContaining({ method: 'POST' }))
    expect(result.solverStatus).toBe('OPTIMAL')
  })

  test('generates schedule in one backend request', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ periodId: 8, periodName: 'Lịch tuần 1', status: 'DRAFT', aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', assignmentCount: 6, warnings: [], errorMessage: null }))

    const result = await backendScheduleService.generateSchedule({
      periodName: 'Lịch tuần 1',
      startDate: '2026-10-05',
      endDate: '2026-10-11',
      versionNo: 1,
      employeeIds: [31, 32],
      shiftTypeIds: [11, 12],
    })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/admin/schedule-periods/generate', expect.objectContaining({ method: 'POST' }))
    expect(result.periodId).toBe(8)
    expect(result.assignmentCount).toBe(6)
  })
})

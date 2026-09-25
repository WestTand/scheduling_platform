import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { buildGenerationNotice, canOptimizeSchedulePeriod, getScheduleGenerationNoticeClass, summarizeOptimizeResult } from './ScheduleGenerationPage.helpers'

describe('ScheduleGenerationPage', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('shows an explicit AI Optimize action in backend mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')

    const { ScheduleGenerationPage } = await import('./ScheduleGenerationPage')
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ScheduleGenerationPage />
      </MemoryRouter>,
    )

    expect(html).toContain('AI Optimize')
  })
})

describe('ScheduleGenerationPage helpers', () => {
  it('enables optimize only when a backend period is selected and not submitting', () => {
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '7', isSubmitting: false })).toBe(true)
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '', isSubmitting: false })).toBe(false)
    expect(canOptimizeSchedulePeriod({ selectedPeriodId: '7', isSubmitting: true })).toBe(false)
  })

  it('styles success and error notices distinctly', () => {
    expect(getScheduleGenerationNoticeClass('success')).toContain('border-blue-200')
    expect(getScheduleGenerationNoticeClass('warning')).toContain('border-amber-200')
    expect(getScheduleGenerationNoticeClass('error')).toContain('border-red-200')
  })

  it('formats optimize output with status and assignment count', () => {
    expect(summarizeOptimizeResult({ aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', assignmentCount: 6 })).toBe('AI: SUCCESS · Solver: OPTIMAL · 6 phân công')
    expect(summarizeOptimizeResult({ aiStatus: 'FAILED', solverStatus: null, assignmentCount: null })).toBe('AI: FAILED')
  })

  it('builds end-user generation notices without raw codes', () => {
    expect(buildGenerationNotice('Lịch A', { aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', assignmentCount: 3, errorMessage: null }).message)
      .toBe('Tạo lịch thành công.')
    expect(buildGenerationNotice('Lịch A', { aiStatus: 'INFEASIBLE', solverStatus: 'INFEASIBLE', assignmentCount: 0, errorMessage: 'x' }).message)
      .toBe('Đã tạo lịch nhưng chưa phân công đủ. Vui lòng xem cảnh báo hoặc chỉnh sửa lịch.')
  })

  it('builds warning notice for infeasible one-shot generation', () => {
    expect(buildGenerationNotice('Lịch tuần 1', {
      aiStatus: 'INFEASIBLE',
      solverStatus: 'INFEASIBLE',
      assignmentCount: 0,
      errorMessage: 'NO_FEASIBLE_SCHEDULE: No schedule satisfies all hard constraints',
    })).toEqual({
      type: 'warning',
      message: 'Đã tạo lịch nhưng chưa phân công đủ. Vui lòng xem cảnh báo hoặc chỉnh sửa lịch.',
    })
  })
})

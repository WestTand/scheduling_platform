import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

import { ScheduleGenerationForm } from './ScheduleGenerationForm'
import { getDefaultGenerationDates, getPaginatedEmployees, getTotalEmployeePages } from './ScheduleGenerationForm.helpers'

describe('ScheduleGenerationForm', () => {
  it('renders backend employee and shift scope without mock seed names', () => {
    const html = renderToStaticMarkup(<ScheduleGenerationForm
      backendMode
      backendEmployees={[{ id: '31', fullName: 'Backend Employee', employeeCode: 'BE-001' }]}
      backendShiftTypes={[{ id: 11, shiftName: 'Backend Shift', shiftCode: 'BACKEND_SHIFT', startTime: '08:00:00', endTime: '17:00:00', status: 'ACTIVE' }]}
      onSubmit={vi.fn()}
      onCancel={vi.fn()}
    />)

    expect(html).toContain('Phạm vi nhân viên')
    expect(html).toContain('Backend Employee')
    expect(html).toContain('Backend Shift')
    expect(html).not.toContain('Nguyễn Thị Lan')
  })

  it('defaults generation dates to local today plus six days', () => {
    const { startDate, endDate } = getDefaultGenerationDates(new Date(2026, 8, 25, 10, 0, 0))
    expect(startDate).toBe('2026-09-25')
    expect(endDate).toBe('2026-10-01')
  })

  it('paginates employee pool to avoid a long page', () => {
    const employees = Array.from({ length: 10 }, (_, index) => ({ id: String(index + 1) }))
    expect(getTotalEmployeePages(employees)).toBe(2)
    expect(getPaginatedEmployees(employees, 1).map((item) => item.id)).toHaveLength(9)
    expect(getPaginatedEmployees(employees, 2).map((item) => item.id)).toEqual(['10'])
  })
})

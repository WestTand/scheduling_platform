import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

import { ScheduleGenerationForm } from './ScheduleGenerationForm'

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
})

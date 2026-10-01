import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { ScheduleList } from '@/components/schedule/ScheduleList'
import { ScheduleStatus, type WorkSchedule } from '@/types/schedule'

const schedule = (status: WorkSchedule['status'], optimizationStatus?: string | null): WorkSchedule => ({
  id: `schedule-${status}`,
  name: `Lịch ${status}`,
  startDate: '2026-09-21',
  endDate: '2026-09-27',
  createdBy: 'manager@example.com',
  createdAt: '2026-09-21T00:00:00',
  employeeCount: 4,
  assignmentCount: 6,
  shiftCount: 3,
  status,
  isMock: false,
  optimizationStatus,
})

describe('ScheduleList', () => {
  it('renders real actions instead of the Mo lich placeholder', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ScheduleList
          schedules={[schedule(ScheduleStatus.DRAFT), schedule(ScheduleStatus.APPROVED)]}
          onApprove={() => undefined}
          onPublish={() => undefined}
          onCancel={() => undefined}
        />
      </MemoryRouter>,
    )

    expect(html).toContain('Xem chi tiết')
    expect(html).toContain('Chỉnh sửa')
    expect(html).toContain('Duyệt')
    expect(html).toContain('Công bố')
    expect(html).toContain('Hủy')
    expect(html).not.toContain('Mở lịch')
  })

  it('disables approve action for infeasible schedules from the list response', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ScheduleList
          schedules={[schedule(ScheduleStatus.DRAFT, 'INFE')]}
          onApprove={() => undefined}
          onPublish={() => undefined}
          onCancel={() => undefined}
        />
      </MemoryRouter>,
    )

    expect(html).toContain('disabled=""')
    expect(html).toContain('Lịch đang bị cảnh báo (INFE) nên không thể duyệt.')
  })
})

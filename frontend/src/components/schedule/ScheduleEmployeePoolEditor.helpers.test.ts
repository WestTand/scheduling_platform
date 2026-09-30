import { describe, expect, it } from 'vitest'

import { buildEmployeePoolPayload, getEmployeePoolSelection } from './ScheduleEmployeePoolEditor.helpers'

const candidates = [
  { id: '31', fullName: 'Nguyễn Văn An', employeeCode: 'EMP001' },
  { id: '32', fullName: 'Trần Thị Bình', employeeCode: 'EMP002' },
  { id: '33', fullName: 'Lê Văn Cường', employeeCode: 'EMP003' },
]

describe('getEmployeePoolSelection', () => {
  it('preselects employees already in the current pool', () => {
    expect(getEmployeePoolSelection(candidates, ['31', '33'])).toEqual(['31', '33'])
  })

  it('returns an empty selection when nothing is preselected', () => {
    expect(getEmployeePoolSelection(candidates, [])).toEqual([])
  })

  it('drops pool ids that are no longer selectable candidates', () => {
    expect(getEmployeePoolSelection(candidates, ['32', '99'])).toEqual(['32'])
  })

  it('returns an empty selection when there are no candidates at all', () => {
    expect(getEmployeePoolSelection([], ['31'])).toEqual([])
  })
})

describe('buildEmployeePoolPayload', () => {
  it('returns an empty string for an empty selection', () => {
    expect(buildEmployeePoolPayload([])).toBe('')
  })

  it('serializes a single selected id as a JSON array of numbers', () => {
    expect(buildEmployeePoolPayload(['1'])).toBe('[1]')
  })

  it('serializes multiple selected ids in the given order', () => {
    expect(buildEmployeePoolPayload(['32', '31'])).toBe('[32,31]')
  })
})

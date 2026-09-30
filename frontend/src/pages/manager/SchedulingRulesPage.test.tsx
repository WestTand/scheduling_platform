import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { SchedulingRulesPage } from './SchedulingRulesPage'

// NOTE: @testing-library/react is NOT a frontend dependency, so only the
// first render (loading state) can be asserted here. The read-only rule
// rendering is covered by ScheduleRulesPage.helpers unit tests.

const createStorage = (): Storage => {
  const values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key) },
    setItem: (key, value) => { values.set(key, value) },
  }
}

describe('SchedulingRulesPage', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exposes a read-only description without edit wording', () => {
    const html = renderToStaticMarkup(<SchedulingRulesPage />)

    expect(html).toContain('Xem quy định bắt buộc')
    expect(html).not.toContain('Lưu quy định')
  })
})

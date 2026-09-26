import { beforeEach, describe, expect, test, vi } from 'vitest'

import { EXPORT_OPTIONS, exportService } from './exportService'
import { AUTH_STORAGE_KEYS } from './authStorage'

describe('exportService', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      get length() { return store.size },
      clear: () => store.clear(),
      getItem: (key: string) => store.get(key) ?? null,
      key: (index: number) => Array.from(store.keys())[index] ?? null,
      removeItem: (key: string) => { store.delete(key) },
      setItem: (key: string, value: string) => { store.set(key, value) },
    })
    vi.stubGlobal('sessionStorage', {
      get length() { return 0 },
      clear: () => {},
      getItem: () => null,
      key: () => null,
      removeItem: () => {},
      setItem: () => {},
    })
    localStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('exposes exactly three supported options', () => {
    expect(EXPORT_OPTIONS.map((option) => option.value)).toEqual(['schedule-xlsx', 'schedule-pdf', 'workload-xlsx'])
  })

  test('downloads schedule xlsx with server filename', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(new Uint8Array([1]), {
      headers: { 'Content-Type': 'application/octet-stream', 'Content-Disposition': 'attachment; filename="schedule-period-48.xlsx"' },
    }))

    const result = await exportService.download('schedule-xlsx', '48')

    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/schedule-periods/48/exports/schedule.xlsx')
    expect(result.filename).toBe('schedule-period-48.xlsx')
  })

  test('downloads schedule pdf from its endpoint', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(new Uint8Array([1]), {
      headers: { 'Content-Type': 'application/pdf' },
    }))

    const result = await exportService.download('schedule-pdf', '48')

    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/schedule-periods/48/exports/schedule.pdf')
    expect(result.filename).toBe('schedule-period-48.pdf')
  })

  test('downloads workload xlsx from its endpoint', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(new Uint8Array([1]), {
      headers: { 'Content-Type': 'application/octet-stream' },
    }))

    const result = await exportService.download('workload-xlsx', '48')

    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/schedule-periods/48/reports/workload.xlsx')
    expect(result.filename).toBe('workload-period-48.xlsx')
  })
})

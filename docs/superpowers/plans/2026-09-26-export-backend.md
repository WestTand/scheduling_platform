# Export Backend Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the manager exports page to the three real backend file-download APIs and remove unsupported options.

**Architecture:** Add a blob-capable download client by extracting the existing auth/refresh-retry flow in `apiClient` into a shared core, add a thin `exportService` mapping the three supported options to their exact endpoint URLs with filename fallbacks, and rework `ExportsPage` around period selection with validation, loading, and notice states. No backend changes.

**Tech Stack:** React 19, TypeScript, Vitest, existing `apiClient` auth/refresh behavior, `backendScheduleService.listPeriods` for the period dropdown.

---

## File Structure

- Modify `frontend/src/services/apiClient.ts`: extract shared auth-retry fetch core, add `requestBlob` returning `{ blob, filename }`.
- Modify `frontend/src/services/apiClient.test.ts`: add blob success and blob error tests.
- Create `frontend/src/services/exportService.ts`: option-to-endpoint mapping, filename fallback, download functions.
- Create `frontend/src/services/exportService.test.ts`: URL and fallback tests.
- Create `frontend/src/pages/manager/ExportsPage.helpers.ts`: selection validation helper.
- Create `frontend/src/pages/manager/ExportsPage.test.tsx`: helper tests.
- Modify `frontend/src/pages/manager/ExportsPage.tsx`: period dropdown, three supported options, download wiring, mock-mode disable.

---

### Task 1: Blob download client

**Files:**
- Modify: `frontend/src/services/apiClient.ts`
- Modify: `frontend/src/services/apiClient.test.ts`

- [ ] **Step 1: Write the failing tests**

In `frontend/src/services/apiClient.test.ts`, change the import line:

```ts
import { requestJson } from '@/services/apiClient'
```

to:

```ts
import { requestBlob, requestJson } from '@/services/apiClient'
```

Append these tests inside the existing `describe('requestJson', ...)` block's parent scope (as a new `describe('requestBlob', ...)` block reusing the file's `jsonResponse` helper and `beforeEach` storage/fetch stubs):

```ts
describe('requestBlob', () => {
  test('returns blob and filename for binary download responses', async () => {
    const bytes = new Uint8Array([1, 2, 3])
    vi.mocked(fetch).mockResolvedValueOnce(new Response(bytes, {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="schedule-period-48.xlsx"',
      },
    }))

    const result = await requestBlob('/api/admin/schedule-periods/48/exports/schedule.xlsx')

    expect(result.filename).toBe('schedule-period-48.xlsx')
    expect(result.blob.size).toBe(3)
  })

  test('throws ApiError with backend message for failed downloads', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ message: 'Schedule period not found' }, { status: 404 }))

    await expect(requestBlob('/api/admin/schedule-periods/999/exports/schedule.xlsx')).rejects.toMatchObject({
      status: 404,
      message: 'Schedule period not found',
    })
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm.cmd test -- apiClient.test.ts`

Workdir: `frontend`

Expected: FAIL with "requestBlob is not a function" (or "does not provide an export").

- [ ] **Step 3: Implement `requestBlob` with shared auth-retry core**

In `frontend/src/services/apiClient.ts`, extract the send + 401-refresh-retry flow from `requestJson` into:

```ts
const fetchWithAuthRetry = async (path: string, options: RequestOptions): Promise<Response> => {
  let response = await sendRequest(path, options, options.auth === false ? null : getStoredToken())

  if (!response.ok && response.status === 401 && options.auth !== false) {
    const refreshedToken = await refreshAccessToken()

    if (refreshedToken) {
      updateStoredToken(refreshedToken)
      response = await sendRequest(path, options, refreshedToken)
    } else {
      clearStoredAuth()
    }
  }

  return response
}
```

Rewrite `requestJson` to use it (behavior identical):

```ts
export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetchWithAuthRetry(path, options)
  const body = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return body as T
}
```

Add filename parsing plus `requestBlob` after `requestJson`:

```ts
const parseAttachmentFilename = (header: string | null): string | null => {
  if (!header) {
    return null
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(header)
  return quoted?.[1] ?? null
}

export async function requestBlob(path: string, options: RequestOptions = {}): Promise<{ blob: Blob; filename: string | null }> {
  const response = await fetchWithAuthRetry(path, options)

  if (!response.ok) {
    const body = await parseResponseBody(response)
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return { blob: await response.blob(), filename: parseAttachmentFilename(response.headers.get('Content-Disposition')) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm.cmd test -- apiClient.test.ts`

Workdir: `frontend`

Expected: PASS, all tests including the 3 pre-existing requestJson tests (refresh retry, refresh failure clears auth, session-scope storage).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services/apiClient.ts frontend/src/services/apiClient.test.ts
git commit -m "feat: add blob download client with auth retry"
```

---

### Task 2: Export service

**Files:**
- Create: `frontend/src/services/exportService.ts`
- Test: `frontend/src/services/exportService.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/services/exportService.test.ts` with this exact content:

```ts
import { beforeEach, describe, expect, test, vi } from 'vitest'

import { EXPORT_OPTIONS, exportService } from './exportService'
import { AUTH_STORAGE_KEYS } from './authStorage'

const jsonResponse = (body: unknown, init: ResponseInit = {}) => new Response(JSON.stringify(body), {
  headers: { 'Content-Type': 'application/json' },
  ...init,
})

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- exportService.test.ts`

Workdir: `frontend`

Expected: FAIL with "Cannot find module './exportService'".

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/services/exportService.ts` with this exact content:

```ts
import { requestBlob } from '@/services/apiClient'

export type ScheduleExportOption = 'schedule-xlsx' | 'schedule-pdf' | 'workload-xlsx'

export const EXPORT_OPTIONS: Array<{ value: ScheduleExportOption; label: string }> = [
  { value: 'schedule-xlsx', label: 'Lịch làm việc (Excel)' },
  { value: 'schedule-pdf', label: 'Lịch làm việc (PDF)' },
  { value: 'workload-xlsx', label: 'Báo cáo workload (Excel)' },
]

const endpointFor = (option: ScheduleExportOption, periodId: string) => {
  const encoded = encodeURIComponent(periodId)
  if (option === 'schedule-pdf') return `/api/admin/schedule-periods/${encoded}/exports/schedule.pdf`
  if (option === 'workload-xlsx') return `/api/admin/schedule-periods/${encoded}/reports/workload.xlsx`
  return `/api/admin/schedule-periods/${encoded}/exports/schedule.xlsx`
}

const fallbackFilename = (option: ScheduleExportOption, periodId: string) => {
  if (option === 'schedule-pdf') return `schedule-period-${periodId}.pdf`
  if (option === 'workload-xlsx') return `workload-period-${periodId}.xlsx`
  return `schedule-period-${periodId}.xlsx`
}

export const exportService = {
  async download(option: ScheduleExportOption, periodId: string) {
    const { blob, filename } = await requestBlob(endpointFor(option, periodId))
    return { blob, filename: filename ?? fallbackFilename(option, periodId) }
  },
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- exportService.test.ts`

Workdir: `frontend`

Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services/exportService.ts frontend/src/services/exportService.test.ts
git commit -m "feat: add schedule export service"
```

---

### Task 3: Rework ExportsPage

**Files:**
- Create: `frontend/src/pages/manager/ExportsPage.helpers.ts`
- Test: `frontend/src/pages/manager/ExportsPage.test.tsx`
- Modify: `frontend/src/pages/manager/ExportsPage.tsx`

- [ ] **Step 1: Write the failing helper test**

Create `frontend/src/pages/manager/ExportsPage.test.tsx` with this exact content:

```tsx
import { describe, expect, test } from 'vitest'

import { validateExportSelection } from './ExportsPage.helpers'

describe('ExportsPage helpers', () => {
  test('requires a selected period', () => {
    expect(validateExportSelection('')).toBe('Vui lòng chọn kỳ lịch để xuất file.')
  })

  test('accepts a selected period', () => {
    expect(validateExportSelection('48')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- ExportsPage.test.tsx`

Workdir: `frontend`

Expected: FAIL with "Cannot find module './ExportsPage.helpers'".

- [ ] **Step 3: Write the helper and rework the page**

Create `frontend/src/pages/manager/ExportsPage.helpers.ts` with this exact content:

```ts
export const validateExportSelection = (periodId: string): string | null => {
  if (!periodId) {
    return 'Vui lòng chọn kỳ lịch để xuất file.'
  }
  return null
}
```

Rewrite `frontend/src/pages/manager/ExportsPage.tsx` with this exact content:

```tsx
import { Download, FileText } from 'lucide-react'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { apiConfig } from '@/config/api'
import { backendScheduleService } from '@/services/backendScheduleService'
import { EXPORT_OPTIONS, exportService, type ScheduleExportOption } from '@/services/exportService'
import { validateExportSelection } from './ExportsPage.helpers'
import type { WorkSchedule } from '@/types/schedule'

export function ExportsPage() {
  const [exportOption, setExportOption] = useState<ScheduleExportOption>('schedule-xlsx')
  const [periods, setPeriods] = useState<WorkSchedule[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState('')
  const [isDownloading, setIsDownloading] = useState(false)
  const [notice, setNotice] = useState('')
  const [isError, setIsError] = useState(false)

  useEffect(() => {
    if (apiConfig.useMockApi) return
    let ignore = false
    backendScheduleService.listPeriods({ status: 'ALL' })
      .then((items) => { if (!ignore) setPeriods(items) })
      .catch((loadError) => {
        if (!ignore) {
          setNotice(loadError instanceof Error ? loadError.message : 'Không thể tải danh sách kỳ lịch.')
          setIsError(true)
        }
      })
    return () => { ignore = true }
  }, [])

  const handleExport = async () => {
    const validationError = validateExportSelection(selectedPeriodId)
    if (validationError) {
      setNotice(validationError)
      setIsError(true)
      return
    }
    setIsDownloading(true)
    try {
      const { blob, filename } = await exportService.download(exportOption, selectedPeriodId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      setNotice(`Đã tải file ${filename}.`)
      setIsError(false)
    } catch (downloadError) {
      setNotice(downloadError instanceof Error ? downloadError.message : 'Không thể xuất file.')
      setIsError(true)
    } finally {
      setIsDownloading(false)
    }
  }

  const selectedLabel = EXPORT_OPTIONS.find((item) => item.value === exportOption)?.label ?? ''
  const selectedPeriod = periods.find((period) => period.id === selectedPeriodId)

  return (
    <div className="space-y-6">
      <PageHeader title="Xuất dữ liệu" description="Tải file lịch làm việc và báo cáo workload từ backend." />

      {notice && <div className={isError ? 'rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700' : 'rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800'}>{notice}</div>}

      <Card className="border-slate-200 bg-white shadow-sm">
        <CardContent className="grid gap-6 p-5 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-5">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Loại file</label>
              <div className="grid gap-2 sm:grid-cols-2">
                {EXPORT_OPTIONS.map((item) => (
                  <button
                    key={item.value}
                    type="button"
                    onClick={() => setExportOption(item.value)}
                    className={`rounded-xl border px-3 py-2.5 text-left text-sm transition ${exportOption === item.value ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 bg-slate-50 text-slate-700'}`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">Kỳ lịch</label>
              <select value={selectedPeriodId} onChange={(event) => setSelectedPeriodId(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none">
                <option value="">{periods.length ? 'Chọn kỳ lịch' : 'Đang tải kỳ lịch...'}</option>
                {periods.map((period) => <option key={period.id} value={period.id}>{period.name} ({period.startDate} - {period.endDate})</option>)}
              </select>
            </div>

            {apiConfig.useMockApi && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">Chế độ mock: xuất file yêu cầu backend mode.</div>}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="mb-3 flex items-center gap-2 text-slate-700"><FileText className="h-4 w-4" />Tóm tắt cấu hình xuất</div>
            <div className="space-y-3 text-sm text-slate-600">
              <div className="flex justify-between gap-3"><span>Loại file</span><strong>{selectedLabel}</strong></div>
              <div className="flex justify-between gap-3"><span>Kỳ lịch</span><strong>{selectedPeriod ? `${selectedPeriod.name} (${selectedPeriod.startDate} - ${selectedPeriod.endDate})` : 'Chưa chọn'}</strong></div>
            </div>

            <Button className="mt-5 w-full" onClick={() => void handleExport()} disabled={apiConfig.useMockApi || isDownloading}><Download className="mr-2 h-4 w-4" />{isDownloading ? 'Đang xuất...' : 'Xuất dữ liệu'}</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm.cmd test -- ExportsPage.test.tsx exportService.test.ts apiClient.test.ts`

Workdir: `frontend`

Expected: PASS, all files.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/manager/ExportsPage.tsx frontend/src/pages/manager/ExportsPage.helpers.ts frontend/src/pages/manager/ExportsPage.test.tsx
git commit -m "feat: wire exports page to backend file downloads"
```

---

### Task 4: Final verification

**Files:**
- No source changes unless verification exposes failures.

- [ ] **Step 1: Run frontend tests**

Run:

```powershell
npm.cmd test -- --run
```

Workdir: `frontend`

Expected: all test files pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Workdir: `frontend`

Expected: exit 0. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 3: Run frontend lint**

Run:

```powershell
npm.cmd run lint
```

Workdir: `frontend`

Expected: 0 errors. Existing Fast Refresh warning in `AuthContext.tsx` is acceptable.

- [ ] **Step 4: Inspect diff**

Run:

```powershell
git branch --show-current
git status --short
git diff --stat
```

Expected: current branch is `feat/dashboard-backend`; only intended export files changed. Do not stage or commit unless the user explicitly asks.

---

## Self-Review Notes

- Spec coverage: the three backend endpoints map to Task 2 (`schedule-xlsx`, `schedule-pdf`, `workload-xlsx`); CSV/unsupported-type removal, period dropdown replacing date/status filters, loading/success/error notices, mock-mode disable, filename fallback, and anchor-download flow map to Task 3; blob client behavior maps to Task 1; all four testing bullets map to Tasks 1-4.
- Placeholder scan: no TBD/TODO placeholders; every step carries exact file paths, complete code, exact commands, and expected outputs.
- Type consistency: `requestBlob` returns `{ blob: Blob; filename: string | null }` in Task 1 and `exportService.download` consumes exactly that shape in Task 2; `ScheduleExportOption` is defined once in `exportService.ts` and imported by type in Task 3; option values, endpoint paths, and fallback filenames are identical across Tasks 2 and 3; the validation message matches the reviewed spec text exactly.

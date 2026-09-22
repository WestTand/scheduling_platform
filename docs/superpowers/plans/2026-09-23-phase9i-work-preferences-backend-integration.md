# Phase 9I Work Preferences Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect employee and manager Work Preferences screens to backend APIs when `VITE_USE_MOCK_API=false` while preserving mock-mode behavior.

**Architecture:** Reuse the existing backend employee preference DTO for both employee and manager flows. Add manager-only backend list/review endpoints, then add frontend backend preference DTOs, adapters, and service switching behind the existing `preferenceService` facade.

**Tech Stack:** Spring Boot 4, Spring MVC/Security/JPA, JUnit/Mockito/AssertJ, React 19, Vite, TypeScript, Vitest.

---

## File Structure

- Modify: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
  - Add manager search and lock queries.
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceService.java`
  - Manager list/approve/reject business rules.
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceController.java`
  - `/api/admin/preferences` endpoints.
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceServiceTest.java`
  - Service-level list/review rule coverage.
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceControllerTest.java`
  - MVC/security/request mapping coverage.
- Modify: `frontend/src/types/management.ts`
  - Add `CANCELLED` to `PreferenceStatus` because backend employee cancel returns `CANCELLED`.
- Create: `frontend/src/types/backendPreference.ts`
  - Backend response/request types.
- Create: `frontend/src/adapters/preferenceAdapter.ts`
  - Backend-to-UI mapping.
- Create: `frontend/src/adapters/preferenceAdapter.test.ts`
  - Adapter regression tests.
- Create: `frontend/src/services/backendPreferenceService.ts`
  - Backend calls for manager and employee flows.
- Create: `frontend/src/services/backendPreferenceService.test.ts`
  - API path/method/body tests.
- Modify: `frontend/src/services/preferenceService.ts`
  - Switch mock/backend by `apiConfig.useMockApi`.
- Modify: `frontend/src/pages/manager/WorkPreferencesPage.tsx`
  - Backend-mode manager review behavior.
- Modify: `frontend/src/pages/employee/EmployeePreferencesPage.tsx`
  - Backend-backed employee list/create/update/cancel behavior.

---

### Task 1: Backend Manager Preference API

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceController.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceServiceTest.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceControllerTest.java`

- [ ] **Step 1: Write failing repository/service/controller tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceServiceTest.java` with tests for:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.PreferenceRepository;

class AdminPreferenceServiceTest {

    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final AdminPreferenceService service = new AdminPreferenceService(preferenceRepository);

    @Test
    void listPreferencesFiltersByStatusAndDateRange() {
        Preference preference = preference(10L, "PENDING");
        when(preferenceRepository.searchForAdmin("PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(preference));

        List<EmployeePreferenceResponse> response = service.listPreferences(
                manager(), "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(EmployeePreferenceResponse::id).containsExactly(10L);
    }

    @Test
    void listPreferencesRejectsInvalidStatus() {
        assertThatThrownBy(() -> service.listPreferences(manager(), "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approvePreferenceOnlyReviewsPendingPreference() {
        Preference preference = preference(20L, "PENDING");
        when(preferenceRepository.findByIdForAdminReview(20L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.approvePreference(manager(), 20L);

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(preferenceRepository).save(preference);
    }

    @Test
    void rejectPreferenceOnlyReviewsPendingPreference() {
        Preference preference = preference(21L, "PENDING");
        when(preferenceRepository.findByIdForAdminReview(21L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.rejectPreference(manager(), 21L);

        assertThat(response.status()).isEqualTo("REJECTED");
    }

    @Test
    void reviewRejectsNonPendingPreference() {
        Preference preference = preference(22L, "APPROVED");
        when(preferenceRepository.findByIdForAdminReview(22L)).thenReturn(Optional.of(preference));

        assertThatThrownBy(() -> service.rejectPreference(manager(), 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Preference preference(Long id, String status) {
        Employee employee = Employee.builder().employeeCode("NV001").fullName("Nhan vien 1").build();
        employee.setId(100L);
        ShiftType shiftType = ShiftType.builder().shiftCode("THONG_TAM").shiftName("Thông tầm").status("ACTIVE").build();
        shiftType.setId(10L);
        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(LocalDate.of(2026, 9, 15))
                .preferenceType("WANT")
                .priority(1)
                .note("Prefer day shift")
                .status(status)
                .build();
        preference.setId(id);
        return preference;
    }
}
```

Create `backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceControllerTest.java` with tests for list, approve, reject, and employee-forbidden access. Follow the existing `AdminSwapRequestControllerTest` pattern: `@WebMvcTest(AdminPreferenceController.class)`, import auth config, mock `AdminPreferenceService`, mock `AuthService`, use `JwtService`, and assert:

```java
mockMvc.perform(get("/api/admin/preferences").header(HttpHeaders.AUTHORIZATION, bearer(manager())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(50));
mockMvc.perform(post("/api/admin/preferences/50/approve").header(HttpHeaders.AUTHORIZATION, bearer(manager())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
mockMvc.perform(post("/api/admin/preferences/50/reject").header(HttpHeaders.AUTHORIZATION, bearer(manager())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
mockMvc.perform(get("/api/admin/preferences").header(HttpHeaders.AUTHORIZATION, bearer(employee())))
        .andExpect(status().isForbidden());
```

- [ ] **Step 2: Run tests to verify RED**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=AdminPreferenceServiceTest,AdminPreferenceControllerTest" test
```

Expected: compilation fails because `AdminPreferenceService`, `AdminPreferenceController`, `PreferenceRepository.searchForAdmin`, and `findByIdForAdminReview` do not exist.

- [ ] **Step 3: Implement repository queries**

Add to `PreferenceRepository`:

```java
    @Query("""
            select p from Preference p
            join fetch p.employee e
            left join fetch p.shiftType st
            where (:status is null or p.status = :status)
            and (:from is null or p.preferenceDate >= :from)
            and (:to is null or p.preferenceDate <= :to)
            order by p.preferenceDate asc, p.id desc
            """)
    List<Preference> searchForAdmin(
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Preference p
            join fetch p.employee e
            left join fetch p.shiftType st
            where p.id = :id
            """)
    Optional<Preference> findByIdForAdminReview(@Param("id") Long id);
```

- [ ] **Step 4: Implement `AdminPreferenceService`**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceService.java`:

```java
package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.repository.PreferenceRepository;

@Service
public class AdminPreferenceService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final PreferenceRepository preferenceRepository;

    public AdminPreferenceService(PreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeePreferenceResponse> listPreferences(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return preferenceRepository.searchForAdmin(normalizedStatus, from, to).stream()
                .map(EmployeePreferenceResponse::from)
                .toList();
    }

    @Transactional
    public EmployeePreferenceResponse approvePreference(CurrentAccountPrincipal principal, Long preferenceId) {
        return reviewPreference(preferenceId, APPROVED);
    }

    @Transactional
    public EmployeePreferenceResponse rejectPreference(CurrentAccountPrincipal principal, Long preferenceId) {
        return reviewPreference(preferenceId, REJECTED);
    }

    private EmployeePreferenceResponse reviewPreference(Long preferenceId, String nextStatus) {
        Preference preference = preferenceRepository.findByIdForAdminReview(preferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw invalidRequest();
        }
        preference.setStatus(nextStatus);
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || "ALL".equals(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference request");
    }
}
```

- [ ] **Step 5: Implement `AdminPreferenceController`**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceController.java`:

```java
package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;

@RestController
@RequestMapping("/api/admin/preferences")
@PreAuthorize("hasRole('MANAGER')")
public class AdminPreferenceController {

    private final AdminPreferenceService adminPreferenceService;

    public AdminPreferenceController(AdminPreferenceService adminPreferenceService) {
        this.adminPreferenceService = adminPreferenceService;
    }

    @GetMapping
    ResponseEntity<List<EmployeePreferenceResponse>> listPreferences(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminPreferenceService.listPreferences(principal, status, from, to));
    }

    @PostMapping("/{preferenceId}/approve")
    ResponseEntity<EmployeePreferenceResponse> approvePreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        return ResponseEntity.ok(adminPreferenceService.approvePreference(principal, preferenceId));
    }

    @PostMapping("/{preferenceId}/reject")
    ResponseEntity<EmployeePreferenceResponse> rejectPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        return ResponseEntity.ok(adminPreferenceService.rejectPreference(principal, preferenceId));
    }
}
```

- [ ] **Step 6: Run focused backend tests**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=AdminPreferenceServiceTest,AdminPreferenceControllerTest,EmployeePreferenceServiceTest,EmployeePreferenceControllerTest" test
```

Expected: tests pass.

- [ ] **Step 7: Commit backend manager API**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceService.java backend/src/main/java/scheduling/scheduling/admin/AdminPreferenceController.java backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminPreferenceControllerTest.java
git commit -m "feat: add manager preference endpoints"
```

---

### Task 2: Frontend Preference Adapter And Backend Service

**Files:**
- Modify: `frontend/src/types/management.ts`
- Create: `frontend/src/types/backendPreference.ts`
- Create: `frontend/src/adapters/preferenceAdapter.ts`
- Create: `frontend/src/adapters/preferenceAdapter.test.ts`
- Create: `frontend/src/services/backendPreferenceService.ts`
- Create: `frontend/src/services/backendPreferenceService.test.ts`

- [ ] **Step 1: Write failing adapter tests**

Create `frontend/src/adapters/preferenceAdapter.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { adaptBackendPreference, toBackendPreferenceType } from './preferenceAdapter'
import type { BackendPreferenceResponse } from '@/types/backendPreference'

describe('preferenceAdapter', () => {
  it('maps backend WANT preference to UI model', () => {
    const response: BackendPreferenceResponse = {
      id: 12,
      preferenceDate: '2026-09-15',
      preferenceType: 'WANT',
      shiftTypeId: 4,
      shiftName: 'Thông tầm',
      priority: 2,
      note: 'Prefer day shift',
      status: 'PENDING',
    }

    expect(adaptBackendPreference(response)).toMatchObject({
      id: '12',
      month: '2026-09',
      type: 'PREFERRED',
      targetLabel: 'Thông tầm',
      priority: 2,
      note: 'Prefer day shift',
      status: 'PENDING',
      submittedAt: '2026-09-15',
    })
  })

  it('maps backend AVOID preference to UI AVOID type', () => {
    expect(adaptBackendPreference({
      id: 13,
      preferenceDate: '2026-10-01',
      preferenceType: 'AVOID',
      shiftTypeId: null,
      shiftName: null,
      priority: 1,
      note: null,
      status: 'APPROVED',
    }).type).toBe('AVOID')
  })

  it('maps UI preference types to backend preference types', () => {
    expect(toBackendPreferenceType('PREFERRED')).toBe('WANT')
    expect(toBackendPreferenceType('PREFER_SHIFT')).toBe('WANT')
    expect(toBackendPreferenceType('AVOID')).toBe('AVOID')
    expect(toBackendPreferenceType('AVOID_SHIFT')).toBe('AVOID')
  })
})
```

- [ ] **Step 2: Run adapter tests to verify RED**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/preferenceAdapter.test.ts
```

Expected: fails because adapter/types do not exist.

- [ ] **Step 3: Add backend preference types and status support**

Modify `frontend/src/types/management.ts` so `PreferenceStatus` includes `CANCELLED`:

```ts
export const PreferenceStatus = {
  PENDING: 'PENDING',
  REVIEWED: 'REVIEWED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
} as const
```

Create `frontend/src/types/backendPreference.ts`:

```ts
export type BackendPreferenceType = 'WANT' | 'AVOID'
export type BackendPreferenceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendPreferenceResponse {
  id: number
  preferenceDate: string
  preferenceType: BackendPreferenceType
  shiftTypeId: number | null
  shiftName: string | null
  priority: number
  note: string | null
  status: BackendPreferenceStatus
}

export interface BackendCreatePreferenceRequest {
  preferenceDate: string
  preferenceType: BackendPreferenceType
  shiftTypeId: number | null
  priority: number
  note?: string
}

export type BackendUpdatePreferenceRequest = BackendCreatePreferenceRequest
```

- [ ] **Step 4: Implement adapter**

Create `frontend/src/adapters/preferenceAdapter.ts`:

```ts
import type { BackendPreferenceResponse, BackendPreferenceType } from '@/types/backendPreference'
import type { PreferenceType, WorkPreference } from '@/types/management'

export const toBackendPreferenceType = (type: PreferenceType): BackendPreferenceType => (
  type === 'AVOID' || type === 'AVOID_SHIFT' ? 'AVOID' : 'WANT'
)

export const adaptBackendPreference = (preference: BackendPreferenceResponse): WorkPreference => ({
  id: String(preference.id),
  employeeName: '',
  employeeEmail: '',
  month: preference.preferenceDate.slice(0, 7),
  type: preference.preferenceType === 'AVOID' ? 'AVOID' : 'PREFERRED',
  note: preference.note ?? '',
  priority: preference.priority,
  submittedAt: preference.preferenceDate,
  status: preference.status,
  targetLabel: preference.shiftName ?? 'Không có ca cụ thể',
})
```

- [ ] **Step 5: Add backend service tests**

Create `frontend/src/services/backendPreferenceService.test.ts` following `backendSwapRequestService.test.ts` style. Mock `@/services/apiClient` and assert:

```ts
expect(requestJson).toHaveBeenCalledWith('/api/admin/preferences?status=PENDING')
expect(requestJson).toHaveBeenCalledWith('/api/admin/preferences/12/approve', { method: 'POST' })
expect(requestJson).toHaveBeenCalledWith('/api/employee/preferences', { method: 'POST', body: { preferenceDate: '2026-09-15', preferenceType: 'WANT', shiftTypeId: 4, priority: 1, note: 'Prefer day shift' } })
expect(requestJson).toHaveBeenCalledWith('/api/employee/preferences/12/cancel', { method: 'POST' })
```

- [ ] **Step 6: Implement backend service**

Create `frontend/src/services/backendPreferenceService.ts`:

```ts
import { adaptBackendPreference, toBackendPreferenceType } from '@/adapters/preferenceAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendCreatePreferenceRequest, BackendPreferenceResponse, BackendUpdatePreferenceRequest } from '@/types/backendPreference'
import type { PreferenceStatus, PreferenceType, WorkPreference } from '@/types/management'

interface ListParams { status?: string; from?: string; to?: string }
interface EmployeePreferenceInput { preferenceDate: string; type: PreferenceType; shiftTypeId: number | null; priority: number; note: string }

const buildListQuery = ({ status = 'ALL', from, to }: ListParams = {}) => {
  const params = new URLSearchParams()
  if (status && status !== 'ALL') params.set('status', status)
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return query ? `?${query}` : ''
}

const toBackendInput = (input: EmployeePreferenceInput): BackendCreatePreferenceRequest => ({
  preferenceDate: input.preferenceDate,
  preferenceType: toBackendPreferenceType(input.type),
  shiftTypeId: input.shiftTypeId,
  priority: input.priority,
  note: input.note.trim() || undefined,
})

export const backendPreferenceService = {
  async listForManager(params: ListParams = {}): Promise<WorkPreference[]> {
    const response = await requestJson<BackendPreferenceResponse[]>(`/api/admin/preferences${buildListQuery(params)}`)
    return response.map(adaptBackendPreference)
  },

  async approveForManager(id: string): Promise<WorkPreference> {
    const response = await requestJson<BackendPreferenceResponse>(`/api/admin/preferences/${encodeURIComponent(id)}/approve`, { method: 'POST' })
    return adaptBackendPreference(response)
  },

  async rejectForManager(id: string): Promise<WorkPreference> {
    const response = await requestJson<BackendPreferenceResponse>(`/api/admin/preferences/${encodeURIComponent(id)}/reject`, { method: 'POST' })
    return adaptBackendPreference(response)
  },

  async listForEmployee(params: ListParams = {}): Promise<WorkPreference[]> {
    const response = await requestJson<BackendPreferenceResponse[]>(`/api/employee/preferences${buildListQuery(params)}`)
    return response.map(adaptBackendPreference)
  },

  async createForEmployee(input: EmployeePreferenceInput): Promise<WorkPreference> {
    const response = await requestJson<BackendPreferenceResponse>('/api/employee/preferences', { method: 'POST', body: toBackendInput(input) })
    return adaptBackendPreference(response)
  },

  async updateForEmployee(id: string, input: EmployeePreferenceInput): Promise<WorkPreference> {
    const body: BackendUpdatePreferenceRequest = toBackendInput(input)
    const response = await requestJson<BackendPreferenceResponse>(`/api/employee/preferences/${encodeURIComponent(id)}`, { method: 'PUT', body })
    return adaptBackendPreference(response)
  },

  async cancelForEmployee(id: string): Promise<void> {
    await requestJson<void>(`/api/employee/preferences/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
  },
}
```

- [ ] **Step 7: Run frontend focused tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/preferenceAdapter.test.ts src/services/backendPreferenceService.test.ts
```

Expected: tests pass.

- [ ] **Step 8: Commit frontend adapter/service**

Run:

```powershell
git add frontend/src/types/management.ts frontend/src/types/backendPreference.ts frontend/src/adapters/preferenceAdapter.ts frontend/src/adapters/preferenceAdapter.test.ts frontend/src/services/backendPreferenceService.ts frontend/src/services/backendPreferenceService.test.ts
git commit -m "feat: add backend preference adapters"
```

---

### Task 3: Wire Manager Work Preferences Page To Backend

**Files:**
- Modify: `frontend/src/services/preferenceService.ts`
- Modify: `frontend/src/pages/manager/WorkPreferencesPage.tsx`

- [ ] **Step 1: Add backend switching to preference service**

Modify `frontend/src/services/preferenceService.ts`:

```ts
import { apiConfig } from '@/config/api'
import { preferenceSeed } from '@/mocks/managementData'
import { backendPreferenceService } from '@/services/backendPreferenceService'
import type { PaginatedResult, PreferenceStatus, PreferenceType, WorkPreference, WorkPreferenceDecision } from '@/types/management'
```

Add shared pagination/filter helper:

```ts
const paginate = (items: WorkPreference[], page: number, pageSize: number): PaginatedResult<WorkPreference> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterPreferences = (items: WorkPreference[], search: string, month: string, type: string, status: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items.filter((preference) => {
    const matchesSearch = !normalizedSearch || [preference.employeeName, preference.note, preference.targetLabel ?? '', preference.relatedShiftCode ?? '']
      .some((value) => value.toLowerCase().includes(normalizedSearch))
    const matchesMonth = month === 'ALL' || preference.month === month
    const matchesType = type === 'ALL' || preference.type === type
    const matchesStatus = status === 'ALL' || preference.status === status
    return matchesSearch && matchesMonth && matchesType && matchesStatus
  })
}
```

Update `list`:

```ts
async list({ page = 1, pageSize = 5, search = '', month = 'ALL', type = 'ALL', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; month?: string; type?: string; status?: string }) {
  if (!apiConfig.useMockApi) {
    const response = await backendPreferenceService.listForManager({ status })
    return paginate(filterPreferences(response, search, month, type, 'ALL'), page, pageSize)
  }
  await delay(350)
  return paginate(filterPreferences(preferences, search, month, type, status), page, pageSize)
}
```

Update `updateStatus`:

```ts
async updateStatus(id: string, decision: WorkPreferenceDecision) {
  if (!apiConfig.useMockApi) {
    return decision.status === 'APPROVED'
      ? backendPreferenceService.approveForManager(id)
      : backendPreferenceService.rejectForManager(id)
  }
  await delay(300)
  const preference = preferences.find((item) => item.id === id)
  if (!preference) throw new Error('Nguyện vọng không tồn tại.')
  preference.status = decision.status === 'APPROVED' ? 'APPROVED' : 'REJECTED'
  preference.managerNote = decision.adminNote
  preference.adminNote = decision.adminNote
  return preference
}
```

- [ ] **Step 2: Relax manager rejection note requirement in backend mode**

In `WorkPreferencesPage.tsx`, import `apiConfig` and change rejection validation:

```ts
if (decisionDialog.type === 'REJECTED' && apiConfig.useMockApi && !decisionState.adminNote.trim()) {
  window.alert('Vui lòng nhập lý do từ chối.')
  return
}
```

- [ ] **Step 3: Run frontend focused tests/build**

Run:

```powershell
npm.cmd test -- src/adapters/preferenceAdapter.test.ts src/services/backendPreferenceService.test.ts
npm.cmd run build
```

Expected: tests and build pass.

- [ ] **Step 4: Commit manager wiring**

Run:

```powershell
git add frontend/src/services/preferenceService.ts frontend/src/pages/manager/WorkPreferencesPage.tsx
git commit -m "feat: connect manager preferences to backend"
```

---

### Task 4: Wire Employee Preferences Page To Backend

**Files:**
- Modify: `frontend/src/services/preferenceService.ts`
- Modify: `frontend/src/pages/employee/EmployeePreferencesPage.tsx`

- [ ] **Step 1: Add employee methods to `preferenceService`**

Add methods:

```ts
async listMine(params: { page?: number; pageSize?: number; search?: string; month?: string; status?: string } = {}) {
  if (!apiConfig.useMockApi) {
    const response = await backendPreferenceService.listForEmployee({ status: params.status ?? 'ALL' })
    return paginate(filterPreferences(response, params.search ?? '', params.month ?? 'ALL', 'ALL', 'ALL'), params.page ?? 1, params.pageSize ?? 50)
  }
  return this.list({ ...params, type: 'ALL', status: params.status ?? 'ALL' })
},

async createMine(input: { preferenceDate: string; type: PreferenceType; shiftTypeId: number | null; priority: number; note: string }) {
  if (!apiConfig.useMockApi) return backendPreferenceService.createForEmployee(input)
  await delay(300)
  const created: WorkPreference = {
    id: `PREF-${Date.now()}`,
    employeeName: 'Bạn',
    month: input.preferenceDate.slice(0, 7),
    type: input.type,
    note: input.note,
    priority: input.priority,
    submittedAt: input.preferenceDate,
    status: 'PENDING',
  }
  preferences = [created, ...preferences]
  return created
},

async updateMine(id: string, input: { preferenceDate: string; type: PreferenceType; shiftTypeId: number | null; priority: number; note: string }) {
  if (!apiConfig.useMockApi) return backendPreferenceService.updateForEmployee(id, input)
  await delay(300)
  const preference = preferences.find((item) => item.id === id)
  if (!preference) throw new Error('Nguyện vọng không tồn tại.')
  preference.month = input.preferenceDate.slice(0, 7)
  preference.type = input.type
  preference.note = input.note
  preference.priority = input.priority
  return preference
},

async cancelMine(id: string) {
  if (!apiConfig.useMockApi) {
    await backendPreferenceService.cancelForEmployee(id)
    return
  }
  await delay(300)
  preferences = preferences.filter((item) => item.id !== id)
}
```

- [ ] **Step 2: Refactor employee page state to use `WorkPreference` and service calls**

In `EmployeePreferencesPage.tsx`:
- Remove `seedPreferences`.
- Import `LoadingState`, `ErrorState`, `EmptyState`, `preferenceService`, and `type WorkPreference`.
- Use `const [preferences, setPreferences] = useState<WorkPreference[]>([])`, `loading`, `error`, and `editing` state.
- Load data in `useEffect` with `preferenceService.listMine({ page: 1, pageSize: 50, search: '', status: statusFilter })`.
- Filter local rows by `search`, `monthFilter`, and `statusFilter`.
- Change form to store `preferenceDate`, `type`, `relatedShiftCode`, `priority`, and `note`.
- Convert selected shift code to backend `shiftTypeId` by finding `shiftDefinitions.find((shift) => shift.code === form.relatedShiftCode)` and using `Number(shift.id)` only if numeric; otherwise pass `null` to avoid sending mock-only shift ids.

Use these handlers:

```ts
const loadPreferences = async () => {
  setLoading(true)
  setError('')
  try {
    const result = await preferenceService.listMine({ page: 1, pageSize: 50, status: statusFilter })
    setPreferences(result.data)
  } catch (loadError) {
    setError(loadError instanceof Error ? loadError.message : 'Không thể tải nguyện vọng.')
  } finally {
    setLoading(false)
  }
}

const handleSubmit = async () => {
  if (!form.note.trim()) {
    window.alert('Nội dung ghi chú không được bỏ trống.')
    return
  }
  const shiftTypeId = null
  try {
    if (editing) {
      await preferenceService.updateMine(editing.id, { preferenceDate: form.preferenceDate, type: form.type as PreferenceType, shiftTypeId, priority: Number(form.priority), note: form.note })
    } else {
      await preferenceService.createMine({ preferenceDate: form.preferenceDate, type: form.type as PreferenceType, shiftTypeId, priority: Number(form.priority), note: form.note })
    }
    setShowForm(false)
    setEditing(null)
    await loadPreferences()
  } catch (submitError) {
    window.alert(submitError instanceof Error ? submitError.message : 'Không thể lưu nguyện vọng.')
  }
}

const handleDelete = async (id: string) => {
  try {
    await preferenceService.cancelMine(id)
    await loadPreferences()
  } catch (cancelError) {
    window.alert(cancelError instanceof Error ? cancelError.message : 'Không thể hủy nguyện vọng.')
  }
}
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Expected: TypeScript build passes.

- [ ] **Step 4: Commit employee wiring**

Run:

```powershell
git add frontend/src/services/preferenceService.ts frontend/src/pages/employee/EmployeePreferencesPage.tsx
git commit -m "feat: connect employee preferences to backend"
```

---

### Task 5: Final Verification

**Files:**
- No source edits expected unless verification finds defects.

- [ ] **Step 1: Run focused backend preference tests**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=AdminPreferenceServiceTest,AdminPreferenceControllerTest,EmployeePreferenceServiceTest,EmployeePreferenceControllerTest" test
```

Expected: tests pass.

- [ ] **Step 2: Run backend package**

Run in `backend/`:

```powershell
./mvnw.cmd -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run frontend tests/lint/build**

Run in `frontend/`:

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Expected: tests pass, lint has 0 errors with the existing `AuthContext.tsx` Fast Refresh warning allowed, build succeeds with existing chunk-size warning allowed.

- [ ] **Step 4: Review diff and status**

Run:

```powershell
git status --short
git diff --stat main...HEAD
```

Expected: only Phase 9I backend/frontend/spec/plan files are tracked in branch commits. Pre-existing untracked docs stay uncommitted.

- [ ] **Step 5: Push branch**

Run:

```powershell
git push -u origin phase9i/work-preferences-backend-integration
```

Expected: branch pushes successfully.

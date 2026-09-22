# Phase 9G Swap Requests Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect swap requests end-to-end so employees create/respond/cancel real requests and managers approve/reject those same backend records.

**Architecture:** Add one small backend employee-safe coworkers endpoint, then mirror the established leave-request frontend integration pattern: backend DTO types, adapter, backend service, public mock/backend-aware service, and page rewiring. Preserve backend swap status semantics (`PENDING_TARGET`, `PENDING_APPROVAL`) instead of collapsing them into generic pending.

**Tech Stack:** Spring Boot 4, JPA, JUnit/MockMvc, React 19, TypeScript, Vite, Vitest, existing `requestJson` API client.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/employee/dto/CoworkerResponse.java`: narrow employee-safe coworker DTO.
- Modify `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`: add active employee coworker query excluding current employee.
- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerController.java`: employee endpoint `GET /api/employee/coworkers`.
- Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeCoworkerControllerTest.java`: route/security/response test for coworker endpoint.
- Create `frontend/src/types/backendSwapRequest.ts`: backend DTOs for swap requests, coworkers, schedule items, and create/respond bodies.
- Create `frontend/src/adapters/swapRequestAdapter.ts`: map backend swap and schedule DTOs into frontend view models.
- Create `frontend/src/services/backendSwapRequestService.ts`: backend HTTP methods for manager and employee swap flows.
- Modify `frontend/src/services/swapRequestService.ts`: route mock/backend calls through `apiConfig.useMockApi` and add employee methods.
- Modify `frontend/src/types/management.ts`: extend `SwapRequestStatus` and `SwapRequest` with backend IDs/statuses needed for action permissions.
- Modify `frontend/src/components/common/StatusBadge.tsx`: display `PENDING_TARGET` and `PENDING_APPROVAL` labels.
- Modify `frontend/src/pages/employee/EmployeeSwapRequestsPage.tsx`: replace local seed state with backend-backed list/create/cancel/respond flow.
- Modify `frontend/src/pages/manager/SwapRequestsPage.tsx`: use backend-backed statuses and remove mock wording.
- Add frontend tests: `frontend/src/adapters/swapRequestAdapter.test.ts`, `frontend/src/services/backendSwapRequestService.test.ts`.

---

### Task 1: Backend Coworker Endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/CoworkerResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeCoworkerControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeCoworkerControllerTest.java`:

```java
package scheduling.scheduling.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.employee.dto.CoworkerResponse;

@WebMvcTest(EmployeeCoworkerController.class)
class EmployeeCoworkerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeCoworkerService employeeCoworkerService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void listsCoworkersForEmployee() throws Exception {
        when(employeeCoworkerService.listCoworkers(any(CurrentAccountPrincipal.class)))
                .thenReturn(List.of(new CoworkerResponse(9L, "EMP009", "Tran Van Nam")));

        mockMvc.perform(get("/api/employee/coworkers")
                        .with(csrf())
                        .with(user(new CurrentAccountPrincipal(7L, "employee", scheduling.scheduling.entity.AccountRole.EMPLOYEE))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].employeeCode").value("EMP009"))
                .andExpect(jsonPath("$[0].fullName").value("Tran Van Nam"));
    }

    @Test
    void rejectsManagerRole() throws Exception {
        mockMvc.perform(get("/api/employee/coworkers")
                        .with(csrf())
                        .with(user(new CurrentAccountPrincipal(1L, "manager", scheduling.scheduling.entity.AccountRole.MANAGER))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run: `./mvnw.cmd -Dtest=EmployeeCoworkerControllerTest test`

Expected: FAIL with compilation errors for missing `EmployeeCoworkerController`, `EmployeeCoworkerService`, and `CoworkerResponse`.

- [ ] **Step 3: Add coworker DTO**

Create `backend/src/main/java/scheduling/scheduling/employee/dto/CoworkerResponse.java`:

```java
package scheduling.scheduling.employee.dto;

import scheduling.scheduling.entity.Employee;

public record CoworkerResponse(Long id, String employeeCode, String fullName) {

    public static CoworkerResponse from(Employee employee) {
        return new CoworkerResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName());
    }
}
```

- [ ] **Step 4: Add repository query**

Modify `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java` by adding this method after `findSchedulableForAi()`:

```java
    @Query("""
            select e from Employee e
            join fetch e.account a
            where e.status = 'ACTIVE'
              and a.role = scheduling.scheduling.entity.AccountRole.EMPLOYEE
              and a.status = scheduling.scheduling.entity.AccountStatus.ACTIVE
              and a.isActive = true
              and e.id <> :currentEmployeeId
            order by e.employeeCode asc, e.fullName asc
            """)
    List<Employee> findActiveCoworkers(Long currentEmployeeId);
```

- [ ] **Step 5: Add coworker service**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerService.java`:

```java
package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CoworkerResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class EmployeeCoworkerService {

    private final EmployeeAccess employeeAccess;
    private final EmployeeRepository employeeRepository;

    public EmployeeCoworkerService(EmployeeAccess employeeAccess, EmployeeRepository employeeRepository) {
        this.employeeAccess = employeeAccess;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<CoworkerResponse> listCoworkers(CurrentAccountPrincipal principal) {
        Employee currentEmployee = employeeAccess.requireActiveEmployee(principal);
        return employeeRepository.findActiveCoworkers(currentEmployee.getId()).stream()
                .map(CoworkerResponse::from)
                .toList();
    }
}
```

- [ ] **Step 6: Add coworker controller**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerController.java`:

```java
package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CoworkerResponse;

@RestController
@RequestMapping("/api/employee/coworkers")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCoworkerController {

    private final EmployeeCoworkerService employeeCoworkerService;

    public EmployeeCoworkerController(EmployeeCoworkerService employeeCoworkerService) {
        this.employeeCoworkerService = employeeCoworkerService;
    }

    @GetMapping
    ResponseEntity<List<CoworkerResponse>> listCoworkers(
            @AuthenticationPrincipal CurrentAccountPrincipal principal) {
        return ResponseEntity.ok(employeeCoworkerService.listCoworkers(principal));
    }
}
```

- [ ] **Step 7: Run focused backend test**

Run: `./mvnw.cmd -Dtest=EmployeeCoworkerControllerTest test`

Expected: PASS, 2 tests.

- [ ] **Step 8: Commit backend coworker endpoint**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/dto/CoworkerResponse.java backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerService.java backend/src/main/java/scheduling/scheduling/employee/EmployeeCoworkerController.java backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java backend/src/test/java/scheduling/scheduling/employee/EmployeeCoworkerControllerTest.java
git commit -m "feat: add employee coworker endpoint"
```

---

### Task 2: Frontend Swap DTOs And Adapters

**Files:**
- Create: `frontend/src/types/backendSwapRequest.ts`
- Create: `frontend/src/adapters/swapRequestAdapter.ts`
- Test: `frontend/src/adapters/swapRequestAdapter.test.ts`
- Modify: `frontend/src/types/management.ts`
- Modify: `frontend/src/components/common/StatusBadge.tsx`

- [ ] **Step 1: Write failing adapter tests**

Create `frontend/src/adapters/swapRequestAdapter.test.ts`:

```ts
import { describe, expect, test } from 'vitest'

import { adaptBackendSwapRequest, adaptBackendScheduleAssignment } from '@/adapters/swapRequestAdapter'
import type { BackendSwapRequestResponse, BackendEmployeeScheduleItemResponse } from '@/types/backendSwapRequest'

describe('swapRequestAdapter', () => {
  test('preserves pending target status and employee ids', () => {
    const response: BackendSwapRequestResponse = {
      id: 15,
      requesterAssignmentId: 100,
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'Nguyen Van A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'Tran Van B' },
      reason: 'Can doi ca',
      status: 'PENDING_TARGET',
      reviewedByAccountId: null,
      reviewedByUsername: null,
      reviewedAt: null,
    }

    expect(adaptBackendSwapRequest(response)).toMatchObject({
      id: '15',
      requesterEmployeeId: '7',
      targetEmployeeId: '9',
      requesterAssignmentId: '100',
      targetAssignmentId: undefined,
      employeeName: 'Nguyen Van A',
      partnerName: 'Tran Van B',
      status: 'PENDING_TARGET',
      reason: 'Can doi ca',
    })
  })

  test('maps employee schedule item into assignment option', () => {
    const item: BackendEmployeeScheduleItemResponse = {
      assignmentId: 200,
      assignmentStatus: 'ASSIGNED',
      assignedSource: 'AUTO',
      locked: false,
      workDate: '2026-09-22',
      shiftTypeId: 3,
      shiftTypeName: 'Truc sang',
      shiftStartTime: '08:00:00',
      shiftEndTime: '16:00:00',
      startsAt: '2026-09-22T01:00:00Z',
      endsAt: '2026-09-22T09:00:00Z',
      workMinutes: 480,
      isTransitionShift: false,
      periodId: 4,
      periodName: 'Tuan 39',
      periodStatus: 'PUBLISHED',
    }

    expect(adaptBackendScheduleAssignment(item)).toEqual({
      id: '200',
      workDate: '2026-09-22',
      shiftName: 'Truc sang',
      shiftStartTime: '08:00:00',
      shiftEndTime: '16:00:00',
      periodName: 'Tuan 39',
      periodStatus: 'PUBLISHED',
    })
  })
})
```

- [ ] **Step 2: Run adapter test to verify RED**

Run: `npm.cmd test -- src/adapters/swapRequestAdapter.test.ts`

Expected: FAIL with missing module or missing exports.

- [ ] **Step 3: Extend frontend swap types**

Modify `frontend/src/types/management.ts`:

```ts
export const SwapRequestStatus = {
  PENDING_TARGET: 'PENDING_TARGET',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
} as const
```

Replace the `SwapRequest` interface with:

```ts
export interface SwapRequest {
  id: string
  requesterEmployeeId?: string
  targetEmployeeId?: string
  requesterAssignmentId?: string
  targetAssignmentId?: string
  employeeName: string
  employeeEmail: string
  currentShiftCode?: ShiftCode
  currentShiftName?: string
  requestedShiftCode?: ShiftCode
  requestedShiftName?: string
  partnerName?: string
  requestedDate: string
  reason: string
  note?: string
  status: SwapRequestStatus
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  managerNote?: string
}

export interface SwapAssignmentOption {
  id: string
  workDate: string
  shiftName: string
  shiftStartTime: string
  shiftEndTime: string
  periodName: string
  periodStatus: string
}

export interface CoworkerOption {
  id: string
  employeeCode: string
  fullName: string
}
```

- [ ] **Step 4: Add backend swap DTO types**

Create `frontend/src/types/backendSwapRequest.ts`:

```ts
export type BackendSwapRequestStatus = 'PENDING_TARGET' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface BackendEmployeeSummary {
  id: number
  employeeCode: string
  fullName: string
}

export interface BackendSwapRequestResponse {
  id: number
  requesterAssignmentId: number
  requesterEmployee: BackendEmployeeSummary
  targetAssignmentId: number | null
  targetEmployee: BackendEmployeeSummary
  reason: string | null
  status: BackendSwapRequestStatus
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
}

export interface BackendCreateSwapRequestRequest {
  requesterAssignmentId: number
  targetEmployeeId: number
  targetAssignmentId: number | null
  reason?: string
}

export interface BackendRespondSwapRequestRequest {
  accept: boolean
}

export interface BackendEmployeeScheduleItemResponse {
  assignmentId: number
  assignmentStatus: string
  assignedSource: string
  locked: boolean
  workDate: string
  shiftTypeId: number
  shiftTypeName: string
  shiftStartTime: string
  shiftEndTime: string
  startsAt: string
  endsAt: string
  workMinutes: number
  isTransitionShift: boolean
  periodId: number
  periodName: string
  periodStatus: string
}

export interface BackendCoworkerResponse {
  id: number
  employeeCode: string
  fullName: string
}
```

- [ ] **Step 5: Add swap adapter**

Create `frontend/src/adapters/swapRequestAdapter.ts`:

```ts
import type { BackendCoworkerResponse, BackendEmployeeScheduleItemResponse, BackendSwapRequestResponse } from '@/types/backendSwapRequest'
import type { CoworkerOption, SwapAssignmentOption, SwapRequest } from '@/types/management'

const fallbackDate = (request: BackendSwapRequestResponse) => request.reviewedAt?.slice(0, 10) ?? new Date().toISOString().slice(0, 10)

export const adaptBackendSwapRequest = (request: BackendSwapRequestResponse): SwapRequest => ({
  id: String(request.id),
  requesterEmployeeId: String(request.requesterEmployee.id),
  targetEmployeeId: String(request.targetEmployee.id),
  requesterAssignmentId: String(request.requesterAssignmentId),
  targetAssignmentId: request.targetAssignmentId == null ? undefined : String(request.targetAssignmentId),
  employeeName: request.requesterEmployee.fullName,
  employeeEmail: '',
  currentShiftName: `Assignment #${request.requesterAssignmentId}`,
  requestedShiftName: request.targetAssignmentId == null ? 'Nhờ nhận ca' : `Assignment #${request.targetAssignmentId}`,
  partnerName: request.targetEmployee.fullName,
  requestedDate: fallbackDate(request),
  reason: request.reason ?? '',
  note: request.status === 'PENDING_TARGET' ? 'Chờ nhân viên được đề nghị phản hồi.' : request.status === 'PENDING_APPROVAL' ? 'Chờ quản lý duyệt.' : undefined,
  status: request.status,
  submittedAt: fallbackDate(request),
  reviewedBy: request.reviewedByUsername ?? undefined,
  reviewedAt: request.reviewedAt ?? undefined,
})

export const adaptBackendScheduleAssignment = (item: BackendEmployeeScheduleItemResponse): SwapAssignmentOption => ({
  id: String(item.assignmentId),
  workDate: item.workDate,
  shiftName: item.shiftTypeName,
  shiftStartTime: item.shiftStartTime,
  shiftEndTime: item.shiftEndTime,
  periodName: item.periodName,
  periodStatus: item.periodStatus,
})

export const adaptBackendCoworker = (coworker: BackendCoworkerResponse): CoworkerOption => ({
  id: String(coworker.id),
  employeeCode: coworker.employeeCode,
  fullName: coworker.fullName,
})
```

- [ ] **Step 6: Update StatusBadge labels**

Modify `frontend/src/components/common/StatusBadge.tsx` display map and variant expression:

```ts
    PENDING_TARGET: 'Chờ nhân viên phản hồi',
    PENDING_APPROVAL: 'Chờ quản lý duyệt',
```

and include both in the secondary variant branch:

```ts
normalized === 'PENDING' || normalized === 'PENDING_TARGET' || normalized === 'PENDING_APPROVAL' || normalized === 'REVIEWED'
```

- [ ] **Step 7: Run adapter test for GREEN**

Run: `npm.cmd test -- src/adapters/swapRequestAdapter.test.ts`

Expected: PASS.

- [ ] **Step 8: Commit frontend DTOs/adapters**

```bash
git add frontend/src/types/backendSwapRequest.ts frontend/src/types/management.ts frontend/src/adapters/swapRequestAdapter.ts frontend/src/adapters/swapRequestAdapter.test.ts frontend/src/components/common/StatusBadge.tsx
git commit -m "feat: add swap request adapters"
```

---

### Task 3: Backend Swap Request Frontend Service

**Files:**
- Create: `frontend/src/services/backendSwapRequestService.ts`
- Test: `frontend/src/services/backendSwapRequestService.test.ts`

- [ ] **Step 1: Write failing service tests**

Create `frontend/src/services/backendSwapRequestService.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { backendSwapRequestService } from '@/services/backendSwapRequestService'
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

const jsonResponse = (body: unknown, init: ResponseInit = {}) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' }, ...init })

describe('backendSwapRequestService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))
  })

  afterEach(() => vi.unstubAllGlobals())

  test('creates an employee swap request with assignment ids', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      id: 1,
      requesterAssignmentId: 100,
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'B' },
      reason: 'Need swap',
      status: 'PENDING_TARGET',
      reviewedByAccountId: null,
      reviewedByUsername: null,
      reviewedAt: null,
    }))

    await backendSwapRequestService.createForEmployee({ requesterAssignmentId: 100, targetEmployeeId: 9, reason: 'Need swap' })

    const request = fetchMock.mock.calls[0]?.[1]
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/employee/swap-requests')
    expect(request).toEqual(expect.objectContaining({ method: 'POST' }))
    expect(request?.body).toBe(JSON.stringify({ requesterAssignmentId: 100, targetEmployeeId: 9, targetAssignmentId: null, reason: 'Need swap' }))
  })

  test('calls manager review endpoints', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValue(jsonResponse({
      id: 1,
      requesterAssignmentId: 100,
      requesterEmployee: { id: 7, employeeCode: 'EMP007', fullName: 'A' },
      targetAssignmentId: null,
      targetEmployee: { id: 9, employeeCode: 'EMP009', fullName: 'B' },
      reason: null,
      status: 'APPROVED',
      reviewedByAccountId: 1,
      reviewedByUsername: 'manager',
      reviewedAt: '2026-09-22T10:00:00',
    }))

    await backendSwapRequestService.updateStatus('1', 'APPROVED')
    await backendSwapRequestService.updateStatus('1', 'REJECTED')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/admin/swap-requests/1/approve')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/admin/swap-requests/1/reject')
  })
})
```

- [ ] **Step 2: Run service tests to verify RED**

Run: `npm.cmd test -- src/services/backendSwapRequestService.test.ts`

Expected: FAIL because `backendSwapRequestService` does not exist.

- [ ] **Step 3: Add backend service implementation**

Create `frontend/src/services/backendSwapRequestService.ts`:

```ts
import { adaptBackendCoworker, adaptBackendScheduleAssignment, adaptBackendSwapRequest } from '@/adapters/swapRequestAdapter'
import { requestJson } from '@/services/apiClient'
import type {
  BackendCoworkerResponse,
  BackendCreateSwapRequestRequest,
  BackendEmployeeScheduleItemResponse,
  BackendRespondSwapRequestRequest,
  BackendSwapRequestResponse,
} from '@/types/backendSwapRequest'
import type { CoworkerOption, SwapAssignmentOption, SwapRequest, SwapRequestStatus } from '@/types/management'

interface ListParams { status?: string }
interface CreateEmployeeSwapInput { requesterAssignmentId: number; targetEmployeeId: number; reason: string }

const buildStatusQuery = (status?: string) => {
  const params = new URLSearchParams()
  if (status && status !== 'ALL') params.set('status', status)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const backendSwapRequestService = {
  async listForManager({ status = 'ALL' }: ListParams = {}): Promise<SwapRequest[]> {
    const response = await requestJson<BackendSwapRequestResponse[]>(`/api/admin/swap-requests${buildStatusQuery(status)}`)
    return response.map(adaptBackendSwapRequest)
  },

  async listForEmployee({ status = 'ALL' }: ListParams = {}): Promise<SwapRequest[]> {
    const response = await requestJson<BackendSwapRequestResponse[]>(`/api/employee/swap-requests${buildStatusQuery(status)}`)
    return response.map(adaptBackendSwapRequest)
  },

  async createForEmployee(input: CreateEmployeeSwapInput): Promise<SwapRequest> {
    const body: BackendCreateSwapRequestRequest = {
      requesterAssignmentId: input.requesterAssignmentId,
      targetEmployeeId: input.targetEmployeeId,
      targetAssignmentId: null,
      reason: input.reason.trim() || undefined,
    }
    const response = await requestJson<BackendSwapRequestResponse>('/api/employee/swap-requests', { method: 'POST', body })
    return adaptBackendSwapRequest(response)
  },

  async cancelForEmployee(id: string): Promise<SwapRequest> {
    const response = await requestJson<BackendSwapRequestResponse>(`/api/employee/swap-requests/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
    return adaptBackendSwapRequest(response)
  },

  async respondForEmployee(id: string, accept: boolean): Promise<SwapRequest> {
    const body: BackendRespondSwapRequestRequest = { accept }
    const response = await requestJson<BackendSwapRequestResponse>(`/api/employee/swap-requests/${encodeURIComponent(id)}/respond`, { method: 'POST', body })
    return adaptBackendSwapRequest(response)
  },

  async updateStatus(id: string, status: SwapRequestStatus): Promise<SwapRequest> {
    const action = status === 'APPROVED' ? 'approve' : 'reject'
    const response = await requestJson<BackendSwapRequestResponse>(`/api/admin/swap-requests/${encodeURIComponent(id)}/${action}`, { method: 'POST' })
    return adaptBackendSwapRequest(response)
  },

  async listMyAssignments(): Promise<SwapAssignmentOption[]> {
    const response = await requestJson<BackendEmployeeScheduleItemResponse[]>('/api/employee/schedule')
    return response.map(adaptBackendScheduleAssignment)
  },

  async listCoworkers(): Promise<CoworkerOption[]> {
    const response = await requestJson<BackendCoworkerResponse[]>('/api/employee/coworkers')
    return response.map(adaptBackendCoworker)
  },
}
```

- [ ] **Step 4: Run service tests for GREEN**

Run: `npm.cmd test -- src/services/backendSwapRequestService.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit backend swap frontend service**

```bash
git add frontend/src/services/backendSwapRequestService.ts frontend/src/services/backendSwapRequestService.test.ts
git commit -m "feat: add backend swap request service"
```

---

### Task 4: Public Swap Service Backend Switch

**Files:**
- Modify: `frontend/src/services/swapRequestService.ts`

- [ ] **Step 1: Update mock/backend-aware service**

Replace `frontend/src/services/swapRequestService.ts` with a version that preserves existing mock behavior and routes backend mode:

```ts
import { apiConfig } from '@/config/api'
import { getShiftDefinition } from '@/configs/shiftDefinitions'
import { swapRequestSeed } from '@/mocks/managementData'
import { backendSwapRequestService } from '@/services/backendSwapRequestService'
import type { CoworkerOption, PaginatedResult, SwapAssignmentOption, SwapRequest, SwapRequestStatus } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let swapRequests: SwapRequest[] = clone(swapRequestSeed).map((item) => ({
  ...item,
  status: item.status === 'PENDING' ? 'PENDING_APPROVAL' : item.status,
}))

const paginate = (items: SwapRequest[], page: number, pageSize: number): PaginatedResult<SwapRequest> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterRequests = (items: SwapRequest[], search: string, status: string, startDate: string, endDate: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items.filter((request) => {
    const currentShift = request.currentShiftCode ? getShiftDefinition(request.currentShiftCode)?.name ?? '' : request.currentShiftName ?? ''
    const requestedShift = request.requestedShiftCode ? getShiftDefinition(request.requestedShiftCode)?.name ?? '' : request.requestedShiftName ?? ''
    const matchesSearch = !normalizedSearch || [request.employeeName, request.partnerName ?? '', request.reason, currentShift, requestedShift].some((value) => value.toLowerCase().includes(normalizedSearch))
    const matchesStatus = status === 'ALL' || request.status === status
    const matchesStartDate = !startDate || request.requestedDate >= startDate
    const matchesEndDate = !endDate || request.requestedDate <= endDate
    return matchesSearch && matchesStatus && matchesStartDate && matchesEndDate
  })
}

export const swapRequestService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL', startDate = '', endDate = '' }: { page?: number; pageSize?: number; search?: string; status?: string; startDate?: string; endDate?: string }) {
    if (!apiConfig.useMockApi) {
      const response = await backendSwapRequestService.listForManager({ status })
      return paginate(filterRequests(response, search, 'ALL', startDate, endDate), page, pageSize)
    }
    await delay(300)
    return paginate(filterRequests(swapRequests, search, status, startDate, endDate), page, pageSize)
  },

  async listMine(params: { page?: number; pageSize?: number; search?: string; status?: string } = {}) {
    if (!apiConfig.useMockApi) {
      const response = await backendSwapRequestService.listForEmployee({ status: params.status ?? 'ALL' })
      return paginate(filterRequests(response, params.search ?? '', 'ALL', '', ''), params.page ?? 1, params.pageSize ?? 5)
    }
    return this.list({ ...params, status: params.status ?? 'ALL' })
  },

  async listMyAssignments(): Promise<SwapAssignmentOption[]> {
    if (!apiConfig.useMockApi) return backendSwapRequestService.listMyAssignments()
    await delay(150)
    return [
      { id: 'assignment-01', workDate: '2026-09-22', shiftName: 'Thông tầm', shiftStartTime: '08:00:00', shiftEndTime: '17:00:00', periodName: 'Mock period', periodStatus: 'PUBLISHED' },
    ]
  },

  async listCoworkers(): Promise<CoworkerOption[]> {
    if (!apiConfig.useMockApi) return backendSwapRequestService.listCoworkers()
    await delay(150)
    return [{ id: '9', employeeCode: 'EMP009', fullName: 'Trần Văn Nam' }]
  },

  async updateStatus(id: string, status: SwapRequestStatus) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.updateStatus(id, status)
    await delay(250)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (status === 'APPROVED' && request.status !== 'PENDING_APPROVAL') throw new Error('Yêu cầu chưa sẵn sàng để duyệt.')
    if (status === 'REJECTED' && request.status !== 'PENDING_TARGET' && request.status !== 'PENDING_APPROVAL') throw new Error('Yêu cầu đã được xử lý, không thể cập nhật lại.')
    request.status = status
    request.reviewedBy = 'Nguyễn Thị Lan'
    request.reviewedAt = new Date().toISOString()
    return request
  },

  async createMine(input: { requesterAssignmentId: string; targetEmployeeId: string; reason: string }) {
    if (!apiConfig.useMockApi) {
      return backendSwapRequestService.createForEmployee({ requesterAssignmentId: Number(input.requesterAssignmentId), targetEmployeeId: Number(input.targetEmployeeId), reason: input.reason })
    }
    await delay(250)
    const newRequest: SwapRequest = { id: `SWAP-${Date.now()}`, requesterAssignmentId: input.requesterAssignmentId, targetEmployeeId: input.targetEmployeeId, employeeName: 'Bạn', employeeEmail: '', currentShiftName: `Assignment #${input.requesterAssignmentId}`, requestedShiftName: 'Nhờ nhận ca', partnerName: `Employee #${input.targetEmployeeId}`, requestedDate: new Date().toISOString().slice(0, 10), reason: input.reason, status: 'PENDING_TARGET', submittedAt: new Date().toISOString() }
    swapRequests = [newRequest, ...swapRequests]
    return newRequest
  },

  async cancelMine(id: string) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.cancelForEmployee(id)
    await delay(200)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (request.status !== 'PENDING_TARGET') throw new Error('Chỉ có yêu cầu đang chờ nhân viên phản hồi mới được hủy.')
    request.status = 'CANCELLED'
    return request
  },

  async respondMine(id: string, accept: boolean) {
    if (!apiConfig.useMockApi) return backendSwapRequestService.respondForEmployee(id, accept)
    await delay(200)
    const request = swapRequests.find((item) => item.id === id)
    if (!request) throw new Error('Yêu cầu đổi ca không tồn tại.')
    if (request.status !== 'PENDING_TARGET') throw new Error('Yêu cầu không còn chờ phản hồi.')
    request.status = accept ? 'PENDING_APPROVAL' : 'REJECTED'
    return request
  },

  reset() {
    swapRequests = clone(swapRequestSeed).map((item) => ({ ...item, status: item.status === 'PENDING' ? 'PENDING_APPROVAL' : item.status }))
  },
}
```

- [ ] **Step 2: Run frontend tests and typecheck via build**

Run: `npm.cmd test`

Expected: PASS.

Run: `npm.cmd run build`

Expected: PASS.

- [ ] **Step 3: Commit public service switch**

```bash
git add frontend/src/services/swapRequestService.ts
git commit -m "feat: wire swap request service to backend"
```

---

### Task 5: Employee Swap Requests Page End-To-End

**Files:**
- Modify: `frontend/src/pages/employee/EmployeeSwapRequestsPage.tsx`

- [ ] **Step 1: Replace seed state with service-backed state**

Rewrite `EmployeeSwapRequestsPage` to:

```ts
// Key implementation requirements for this file:
// imports: LoadingState, ErrorState, EmptyState, swapRequestService, useAuth, type SwapRequest/CoworkerOption/SwapAssignmentOption.
// state: loading, error, requests PaginatedResult<SwapRequest>, assignments, coworkers, form { requesterAssignmentId, targetEmployeeId, reason }.
// loadData(): calls swapRequestService.listMine({ page: 1, pageSize: 50, search, status: statusFilter }) plus listMyAssignments/listCoworkers when create form opens.
// handleSubmit(): validates requesterAssignmentId, targetEmployeeId, reason.trim(); checks selected assignment periodStatus === 'PUBLISHED'; calls createMine; closes form; reloads.
// handleCancel(id): calls cancelMine; reloads.
// handleRespond(id, accept): calls respondMine; reloads.
// permission helpers:
// const currentEmployeeId = user?.id
// canCancel = item.requesterEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET'
// canRespond = item.targetEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET'
```

Use this exact action rendering pattern inside the table actions column:

```tsx
<div className="flex items-center gap-2">
  <Button variant="ghost" size="icon" onClick={() => setSelected(item)} aria-label="Xem chi tiết"><Eye className="h-4 w-4" /></Button>
  {item.requesterEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET' && (
    <Button variant="ghost" size="icon" onClick={() => void handleCancel(item.id)} aria-label="Hủy yêu cầu"><X className="h-4 w-4" /></Button>
  )}
  {item.targetEmployeeId === currentEmployeeId && item.status === 'PENDING_TARGET' && (
    <>
      <Button variant="ghost" size="icon" onClick={() => void handleRespond(item.id, true)} aria-label="Đồng ý đổi ca"><Check className="h-4 w-4" /></Button>
      <Button variant="ghost" size="icon" onClick={() => void handleRespond(item.id, false)} aria-label="Từ chối đổi ca"><X className="h-4 w-4" /></Button>
    </>
  )}
</div>
```

- [ ] **Step 2: Run frontend build**

Run: `npm.cmd run build`

Expected: PASS.

- [ ] **Step 3: Commit employee page**

```bash
git add frontend/src/pages/employee/EmployeeSwapRequestsPage.tsx
git commit -m "feat: connect employee swap requests to backend"
```

---

### Task 6: Manager Swap Requests Page Status Rules

**Files:**
- Modify: `frontend/src/pages/manager/SwapRequestsPage.tsx`

- [ ] **Step 1: Update statuses and labels**

In `statusOptions`, replace `PENDING` with both pending states:

```ts
const statusOptions = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING_TARGET', label: 'Chờ nhân viên phản hồi' },
  { value: 'PENDING_APPROVAL', label: 'Chờ quản lý duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]
```

Update stats to count `PENDING_TARGET` and `PENDING_APPROVAL` separately or as one pending total:

```ts
pending: data.data.filter((item) => item.status === 'PENDING_TARGET' || item.status === 'PENDING_APPROVAL').length,
```

- [ ] **Step 2: Update action enablement**

Change approve disabled rule to:

```tsx
disabled={item.status !== 'PENDING_APPROVAL'}
```

Change reject disabled rule to:

```tsx
disabled={item.status !== 'PENDING_TARGET' && item.status !== 'PENDING_APPROVAL'}
```

Remove “mock state” from confirm description:

```tsx
description={decisionDialog?.type === 'APPROVED' ? 'Bạn xác nhận duyệt yêu cầu đổi ca này.' : 'Bạn xác nhận từ chối yêu cầu đổi ca này.'}
```

- [ ] **Step 3: Make shift rendering backend-safe**

Replace shift renderers with fallback names:

```tsx
render: (item: SwapRequest) => {
  const shift = item.currentShiftCode ? getShiftDefinition(item.currentShiftCode) : null
  return <span>{shift ? `${shift.name} · ${formatShiftRangeLabel(shift)}` : item.currentShiftName ?? 'Assignment chưa rõ'}</span>
}
```

and for requested:

```tsx
render: (item: SwapRequest) => {
  const shift = item.requestedShiftCode ? getShiftDefinition(item.requestedShiftCode) : null
  return <span>{shift ? `${shift.name} · ${formatShiftRangeLabel(shift)}` : item.requestedShiftName ?? 'Nhờ nhận ca'}</span>
}
```

- [ ] **Step 4: Run frontend build**

Run: `npm.cmd run build`

Expected: PASS.

- [ ] **Step 5: Commit manager page**

```bash
git add frontend/src/pages/manager/SwapRequestsPage.tsx
git commit -m "feat: connect manager swap requests to backend"
```

---

### Task 7: Final Verification And Push

**Files:**
- No new files unless fixing issues found by verification.

- [ ] **Step 1: Run backend focused test**

Run: `./mvnw.cmd -Dtest=EmployeeCoworkerControllerTest test`

Expected: PASS.

- [ ] **Step 2: Run frontend tests**

Run: `npm.cmd test`

Expected: PASS for all Vitest tests.

- [ ] **Step 3: Run frontend lint**

Run: `npm.cmd run lint`

Expected: 0 errors. Existing warning in `AuthContext.tsx` may remain.

- [ ] **Step 4: Run frontend build**

Run: `npm.cmd run build`

Expected: PASS.

- [ ] **Step 5: Run backend package**

Run: `./mvnw.cmd -DskipTests package`

Expected: BUILD SUCCESS.

- [ ] **Step 6: Live API smoke checks**

Use fresh tokens. Run in PowerShell from repo root:

```powershell
$employeeLogin = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body '{"username":"phat143","password":"Phat323255"}' -SessionVariable employeeSession
$employeeHeaders = @{ Authorization = "Bearer $($employeeLogin.accessToken)" }
$schedule = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/employee/schedule" -Headers $employeeHeaders -WebSession $employeeSession
"scheduleCount=$($schedule.Count)"
$coworkers = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/employee/coworkers" -Headers $employeeHeaders -WebSession $employeeSession
"coworkerCount=$($coworkers.Count)"
$swaps = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/employee/swap-requests" -Headers $employeeHeaders -WebSession $employeeSession
"employeeSwapCount=$($swaps.Count)"
$managerLogin = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body '{"username":"tx-test-manager","password":"1111@"}' -SessionVariable managerSession
$managerHeaders = @{ Authorization = "Bearer $($managerLogin.accessToken)" }
$managerSwaps = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/admin/swap-requests" -Headers $managerHeaders -WebSession $managerSession
"managerSwapCount=$($managerSwaps.Count)"
```

Expected: all commands return 200; counts print. If `scheduleCount=0`, document that create verification needs published schedule test data.

- [ ] **Step 7: Check git status**

Run: `git status --short --branch`

Expected: branch only contains unrelated untracked docs, or clean after intended commits.

- [ ] **Step 8: Push branch**

Run: `git push`

Expected: branch updates on GitHub.

---

## Self-Review Checklist

- Spec coverage: employee list/create/cancel/respond, manager list/approve/reject, coworker endpoint, status mapping, mock preservation, tests, and live verification are covered by tasks above.
- Placeholder scan: no incomplete placeholder markers are present in implementation steps.
- Type consistency: `PENDING_TARGET` and `PENDING_APPROVAL` are used consistently across backend DTOs, frontend status type, adapter, service, and page rules.

# Phase 9J Notifications, Activity Logs, and Real Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect notifications, activity logs, and sidebar badges to backend data in backend mode while keeping mock mode intact.

**Architecture:** Reuse existing backend notification/activity endpoints, add one role-aware navigation summary endpoint for badge counts, and add frontend backend adapters/services behind existing facade services. Sidebar reads counts through a single summary facade instead of mock seeds in backend mode.

**Tech Stack:** Spring Boot 4, Spring Data JPA, JUnit/Mockito, React, TypeScript, Vitest, Vite.

---

## File Structure

Backend files:

- Create `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryResponse.java`: DTO for sidebar counts.
- Create `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryService.java`: role-aware count aggregation.
- Create `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryController.java`: `GET /api/me/navigation-summary`.
- Modify `backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java`: add unread count query.
- Modify `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`: add manager and employee pending count queries if not already present.
- Modify `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`: add manager and employee pending count queries if not already present.
- Modify `backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java`: add manager and employee pending count queries if not already present.
- Test `backend/src/test/java/scheduling/scheduling/navigation/NavigationSummaryServiceTest.java`.
- Test `backend/src/test/java/scheduling/scheduling/navigation/NavigationSummaryControllerTest.java`.

Frontend files:

- Create `frontend/src/types/backendNotification.ts`: backend notification DTO type.
- Create `frontend/src/types/backendActivityLog.ts`: backend activity log DTO type.
- Create `frontend/src/types/navigationSummary.ts`: summary count type.
- Create `frontend/src/adapters/notificationAdapter.ts`: backend notification to UI mapping.
- Create `frontend/src/adapters/notificationAdapter.test.ts`: notification adapter tests.
- Create `frontend/src/adapters/activityLogAdapter.ts`: backend activity log to UI mapping.
- Create `frontend/src/adapters/activityLogAdapter.test.ts`: activity log adapter tests.
- Create `frontend/src/services/backendNotificationService.ts`: backend notification API client.
- Create `frontend/src/services/backendNotificationService.test.ts`: backend notification service tests.
- Create `frontend/src/services/backendActivityLogService.ts`: backend activity log API client.
- Create `frontend/src/services/backendActivityLogService.test.ts`: backend activity log service tests.
- Create `frontend/src/services/navigationSummaryService.ts`: mock/backend count facade.
- Create `frontend/src/services/navigationSummaryService.test.ts`: summary service tests.
- Modify `frontend/src/services/notificationService.ts`: delegate to backend service when `VITE_USE_MOCK_API=false`.
- Modify `frontend/src/services/activityLogService.ts`: delegate to backend service when `VITE_USE_MOCK_API=false`.
- Modify `frontend/src/pages/manager/NotificationsPage.tsx`: hide create panel in backend mode and use real service data.
- Modify `frontend/src/pages/employee/EmployeeNotificationsPage.tsx`: replace local array with service, loading/error/empty states.
- Modify `frontend/src/pages/manager/ActivityLogsPage.tsx`: keep facade usage and align filters with backend service.
- Modify `frontend/src/components/layout/Sidebar.tsx`: async summary counts for badges.
- Modify `frontend/src/components/layout/Sidebar.test.tsx`: badge regression tests for real counts.

---

### Task 1: Backend Navigation Summary Endpoint

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryService.java`
- Create: `backend/src/main/java/scheduling/scheduling/navigation/NavigationSummaryController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/navigation/NavigationSummaryServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/navigation/NavigationSummaryControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/scheduling/scheduling/navigation/NavigationSummaryServiceTest.java`:

```java
package scheduling.scheduling.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.NotificationRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@ExtendWith(MockitoExtension.class)
class NavigationSummaryServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock PreferenceRepository preferenceRepository;
    @Mock SwapRequestRepository swapRequestRepository;
    @InjectMocks NavigationSummaryService service;

    @Test
    void managerSummaryIncludesPendingWorkflowCountsAndUnreadNotifications() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(10L, "manager", AccountRole.MANAGER);
        Account account = Account.builder().username("manager").role(AccountRole.MANAGER).build();
        account.setId(10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(leaveRequestRepository.countByStatus("PENDING")).thenReturn(2L);
        when(preferenceRepository.countByStatus("PENDING")).thenReturn(3L);
        when(swapRequestRepository.countByStatus("PENDING_APPROVAL")).thenReturn(4L);
        when(notificationRepository.countUnreadForRecipient(10L)).thenReturn(5L);

        NavigationSummaryResponse response = service.getSummary(principal);

        assertThat(response.pendingLeaveRequests()).isEqualTo(2L);
        assertThat(response.pendingPreferences()).isEqualTo(3L);
        assertThat(response.pendingSwapRequests()).isEqualTo(4L);
        assertThat(response.unreadNotifications()).isEqualTo(5L);
        assertThat(response.pendingSchedules()).isZero();
    }

    @Test
    void employeeSummaryScopesWorkflowCountsToOwnEmployee() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(20L, "employee", AccountRole.EMPLOYEE);
        Employee employee = Employee.builder().employeeCode("NV001").fullName("Nhan vien").status("ACTIVE").build();
        employee.setId(7L);
        Account account = Account.builder().username("employee").role(AccountRole.EMPLOYEE).employee(employee).build();
        account.setId(20L);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));
        when(leaveRequestRepository.countByEmployeeIdAndStatus(7L, "PENDING")).thenReturn(1L);
        when(preferenceRepository.countByEmployeeIdAndStatus(7L, "PENDING")).thenReturn(2L);
        when(swapRequestRepository.countPendingForEmployee(7L)).thenReturn(3L);
        when(notificationRepository.countUnreadForRecipient(20L)).thenReturn(4L);

        NavigationSummaryResponse response = service.getSummary(principal);

        assertThat(response.pendingLeaveRequests()).isEqualTo(1L);
        assertThat(response.pendingPreferences()).isEqualTo(2L);
        assertThat(response.pendingSwapRequests()).isEqualTo(3L);
        assertThat(response.unreadNotifications()).isEqualTo(4L);
        assertThat(response.pendingSchedules()).isZero();
    }
}
```

- [ ] **Step 2: Run service tests to verify they fail**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=NavigationSummaryServiceTest" test
```

Expected: FAIL because `NavigationSummaryService`, `NavigationSummaryResponse`, and repository count methods do not exist.

- [ ] **Step 3: Add repository count methods**

In `NotificationRepository.java`, add:

```java
@Query("""
        select count(n)
        from Notification n
        where n.recipient.id = :recipientId
          and n.read = false
        """)
long countUnreadForRecipient(@Param("recipientId") Long recipientId);
```

In `LeaveRequestRepository.java`, add:

```java
long countByStatus(String status);

long countByEmployeeIdAndStatus(Long employeeId, String status);
```

In `PreferenceRepository.java`, add:

```java
long countByStatus(String status);

long countByEmployeeIdAndStatus(Long employeeId, String status);
```

In `SwapRequestRepository.java`, add:

```java
long countByStatus(String status);

@Query("""
        select count(s)
        from SwapRequest s
        where (s.requesterEmployee.id = :employeeId or s.targetEmployee.id = :employeeId)
          and s.status in ('PENDING_TARGET', 'PENDING_APPROVAL')
        """)
long countPendingForEmployee(@Param("employeeId") Long employeeId);
```

- [ ] **Step 4: Add response DTO**

Create `NavigationSummaryResponse.java`:

```java
package scheduling.scheduling.navigation;

public record NavigationSummaryResponse(
        long pendingLeaveRequests,
        long pendingPreferences,
        long pendingSwapRequests,
        long unreadNotifications,
        long pendingSchedules) {
}
```

- [ ] **Step 5: Add summary service**

Create `NavigationSummaryService.java`:

```java
package scheduling.scheduling.navigation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.NotificationRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class NavigationSummaryService {

    private static final String PENDING = "PENDING";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PreferenceRepository preferenceRepository;
    private final SwapRequestRepository swapRequestRepository;

    public NavigationSummaryService(
            AccountRepository accountRepository,
            NotificationRepository notificationRepository,
            LeaveRequestRepository leaveRequestRepository,
            PreferenceRepository preferenceRepository,
            SwapRequestRepository swapRequestRepository) {
        this.accountRepository = accountRepository;
        this.notificationRepository = notificationRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.preferenceRepository = preferenceRepository;
        this.swapRequestRepository = swapRequestRepository;
    }

    @Transactional(readOnly = true)
    public NavigationSummaryResponse getSummary(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        long unreadNotifications = notificationRepository.countUnreadForRecipient(account.getId());

        if (account.getRole() == AccountRole.MANAGER || account.getRole() == AccountRole.ADMIN) {
            return new NavigationSummaryResponse(
                    leaveRequestRepository.countByStatus(PENDING),
                    preferenceRepository.countByStatus(PENDING),
                    swapRequestRepository.countByStatus(PENDING_APPROVAL),
                    unreadNotifications,
                    0L);
        }

        Employee employee = account.getEmployee();
        if (employee == null) {
            return new NavigationSummaryResponse(0L, 0L, 0L, unreadNotifications, 0L);
        }

        return new NavigationSummaryResponse(
                leaveRequestRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                preferenceRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                swapRequestRepository.countPendingForEmployee(employee.getId()),
                unreadNotifications,
                0L);
    }
}
```

- [ ] **Step 6: Run service tests to verify they pass**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=NavigationSummaryServiceTest" test
```

Expected: PASS.

- [ ] **Step 7: Write failing controller test**

Create `NavigationSummaryControllerTest.java`:

```java
package scheduling.scheduling.navigation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.AccountRole;

@WebMvcTest(NavigationSummaryController.class)
class NavigationSummaryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NavigationSummaryService service;

    @Test
    void returnsNavigationSummaryForAuthenticatedUser() throws Exception {
        when(service.getSummary(any(CurrentAccountPrincipal.class)))
                .thenReturn(new NavigationSummaryResponse(1L, 2L, 3L, 4L, 0L));

        mockMvc.perform(get("/api/me/navigation-summary")
                        .with(csrf())
                        .with(user(new CurrentAccountPrincipal(10L, "manager", AccountRole.MANAGER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingLeaveRequests").value(1))
                .andExpect(jsonPath("$.pendingPreferences").value(2))
                .andExpect(jsonPath("$.pendingSwapRequests").value(3))
                .andExpect(jsonPath("$.unreadNotifications").value(4))
                .andExpect(jsonPath("$.pendingSchedules").value(0));
    }
}
```

- [ ] **Step 8: Run controller test to verify it fails**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=NavigationSummaryControllerTest" test
```

Expected: FAIL because `NavigationSummaryController` does not exist.

- [ ] **Step 9: Add controller**

Create `NavigationSummaryController.java`:

```java
package scheduling.scheduling.navigation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/me/navigation-summary")
@PreAuthorize("isAuthenticated()")
public class NavigationSummaryController {

    private final NavigationSummaryService navigationSummaryService;

    public NavigationSummaryController(NavigationSummaryService navigationSummaryService) {
        this.navigationSummaryService = navigationSummaryService;
    }

    @GetMapping
    ResponseEntity<NavigationSummaryResponse> getSummary(@AuthenticationPrincipal CurrentAccountPrincipal principal) {
        return ResponseEntity.ok(navigationSummaryService.getSummary(principal));
    }
}
```

- [ ] **Step 10: Run backend focused tests**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=NavigationSummaryServiceTest,NavigationSummaryControllerTest" test
```

Expected: PASS.

- [ ] **Step 11: Commit backend summary endpoint**

Run in repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/navigation backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java backend/src/main/java/scheduling/scheduling/repository/SwapRequestRepository.java backend/src/test/java/scheduling/scheduling/navigation
git commit -m "feat: add navigation summary counts"
```

---

### Task 2: Frontend Backend Notification Service

**Files:**
- Create: `frontend/src/types/backendNotification.ts`
- Create: `frontend/src/adapters/notificationAdapter.ts`
- Create: `frontend/src/adapters/notificationAdapter.test.ts`
- Create: `frontend/src/services/backendNotificationService.ts`
- Create: `frontend/src/services/backendNotificationService.test.ts`
- Modify: `frontend/src/services/notificationService.ts`

- [ ] **Step 1: Write failing adapter tests**

Create `frontend/src/adapters/notificationAdapter.test.ts`:

```ts
import { describe, expect, test } from 'vitest'

import { adaptBackendNotification } from '@/adapters/notificationAdapter'
import type { BackendNotificationResponse } from '@/types/backendNotification'

const baseResponse: BackendNotificationResponse = {
  id: 10,
  title: 'Đơn nghỉ đã được duyệt',
  content: 'Đơn nghỉ của bạn đã được duyệt.',
  notificationType: 'LEAVE_APPROVED',
  read: false,
  createdAt: '2026-09-23T08:00:00',
}

describe('adaptBackendNotification', () => {
  test('maps backend notification fields to UI item', () => {
    expect(adaptBackendNotification(baseResponse)).toEqual({
      id: '10',
      title: 'Đơn nghỉ đã được duyệt',
      content: 'Đơn nghỉ của bạn đã được duyệt.',
      type: 'LEAVE',
      priority: 'MEDIUM',
      recipient: 'Bạn',
      recipientType: 'SELECTED',
      createdAt: '2026-09-23T08:00:00',
      isRead: false,
    })
  })

  test('falls back unknown notification types to GENERAL', () => {
    expect(adaptBackendNotification({ ...baseResponse, notificationType: 'CUSTOM_EVENT' }).type).toBe('GENERAL')
  })
})
```

- [ ] **Step 2: Run adapter tests to verify they fail**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/notificationAdapter.test.ts
```

Expected: FAIL because adapter/type files do not exist.

- [ ] **Step 3: Add backend notification type and adapter**

Create `frontend/src/types/backendNotification.ts`:

```ts
export interface BackendNotificationResponse {
  id: number
  title: string
  content: string
  notificationType: string
  read: boolean
  createdAt: string
}
```

Create `frontend/src/adapters/notificationAdapter.ts`:

```ts
import type { BackendNotificationResponse } from '@/types/backendNotification'
import { NotificationType, type NotificationItem, type NotificationType as UiNotificationType } from '@/types/management'

const toNotificationType = (type: string): UiNotificationType => {
  if (type.startsWith('SCHEDULE')) return NotificationType.SCHEDULE
  if (type.startsWith('LEAVE')) return NotificationType.LEAVE
  if (type.startsWith('PREFERENCE')) return NotificationType.PREFERENCE
  if (type.startsWith('SWAP')) return NotificationType.SWAP
  if (type in NotificationType) return type as UiNotificationType
  return NotificationType.GENERAL
}

export const adaptBackendNotification = (response: BackendNotificationResponse): NotificationItem => ({
  id: String(response.id),
  title: response.title,
  content: response.content,
  type: toNotificationType(response.notificationType),
  priority: 'MEDIUM',
  recipient: 'Bạn',
  recipientType: 'SELECTED',
  createdAt: response.createdAt,
  isRead: response.read,
})
```

- [ ] **Step 4: Run adapter tests to verify they pass**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/notificationAdapter.test.ts
```

Expected: PASS.

- [ ] **Step 5: Write failing backend notification service tests**

Create `frontend/src/services/backendNotificationService.test.ts`:

```ts
import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendNotificationService } from '@/services/backendNotificationService'
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

const jsonResponse = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

describe('backendNotificationService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('lists notifications with read filter and client-side pagination', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { id: 1, title: 'A', content: 'Leave approved', notificationType: 'LEAVE_APPROVED', read: false, createdAt: '2026-09-23T08:00:00' },
      { id: 2, title: 'B', content: 'Schedule published', notificationType: 'SCHEDULE_PUBLISHED', read: true, createdAt: '2026-09-23T09:00:00' },
    ]))

    const result = await backendNotificationService.list({ page: 1, pageSize: 1, search: 'leave', type: 'ALL', status: 'UNREAD' })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/employee/notifications?read=false', expect.any(Object))
    expect(result.total).toBe(1)
    expect(result.data[0]?.title).toBe('A')
  })

  test('marks one notification read and marks all read', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ id: 1, title: 'A', content: 'C', notificationType: 'GENERAL', read: true, createdAt: '2026-09-23T08:00:00' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    const item = await backendNotificationService.markRead('1')
    await backendNotificationService.markAllRead()

    expect(item.isRead).toBe(true)
    expect(fetch).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/employee/notifications/1/read', expect.objectContaining({ method: 'PATCH' }))
    expect(fetch).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/employee/notifications/read-all', expect.objectContaining({ method: 'PATCH' }))
  })
})
```

- [ ] **Step 6: Run service tests to verify they fail**

Run in `frontend/`:

```powershell
npm.cmd test -- src/services/backendNotificationService.test.ts
```

Expected: FAIL because `backendNotificationService` does not exist.

- [ ] **Step 7: Add backend notification service**

Create `frontend/src/services/backendNotificationService.ts`:

```ts
import { adaptBackendNotification } from '@/adapters/notificationAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { BackendNotificationResponse } from '@/types/backendNotification'
import type { NotificationItem, PaginatedResult } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  type?: string
  status?: string
}

const paginate = (items: NotificationItem[], page: number, pageSize: number): PaginatedResult<NotificationItem> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

const filterItems = (items: NotificationItem[], search: string, type: string) => {
  const normalized = search.trim().toLowerCase()
  return items.filter((item) => {
    const matchesSearch = !normalized || item.title.toLowerCase().includes(normalized) || item.content.toLowerCase().includes(normalized)
    const matchesType = type === 'ALL' || item.type === type
    return matchesSearch && matchesType
  })
}

export const backendNotificationService = {
  async list({ page = 1, pageSize = 5, search = '', type = 'ALL', status = 'ALL' }: ListParams = {}) {
    const params = new URLSearchParams()
    if (status === 'READ') params.set('read', 'true')
    if (status === 'UNREAD') params.set('read', 'false')
    const query = params.toString()
    const response = await requestJson<BackendNotificationResponse[]>(`/api/employee/notifications${query ? `?${query}` : ''}`)
    return paginate(filterItems(response.map(adaptBackendNotification), search, type), page, pageSize)
  },

  async markRead(id: string) {
    const response = await requestJson<BackendNotificationResponse>(`/api/employee/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' })
    return adaptBackendNotification(response)
  },

  async markAllRead() {
    await requestVoid('/api/employee/notifications/read-all', { method: 'PATCH' })
  },
}
```

- [ ] **Step 8: Update notification facade**

Modify `frontend/src/services/notificationService.ts` imports and backend branches:

```ts
import { apiConfig } from '@/config/api'
import { backendNotificationService } from '@/services/backendNotificationService'
import { notificationSeed } from '@/mocks/managementData'
import type { NotificationItem, NotificationPriority, NotificationType, PaginatedResult } from '@/types/management'
```

At the start of `list()` add:

```ts
if (!apiConfig.useMockApi) {
  return backendNotificationService.list({ page, pageSize, search, type, status })
}
```

At the start of `markRead()` add:

```ts
if (!apiConfig.useMockApi) {
  return backendNotificationService.markRead(id)
}
```

At the start of `markAllRead()` add:

```ts
if (!apiConfig.useMockApi) {
  await backendNotificationService.markAllRead()
  return []
}
```

At the start of `create()` add:

```ts
if (!apiConfig.useMockApi) {
  throw new Error('Backend hiện chưa hỗ trợ tạo thông báo thủ công.')
}
```

- [ ] **Step 9: Run frontend focused tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/notificationAdapter.test.ts src/services/backendNotificationService.test.ts
```

Expected: PASS.

- [ ] **Step 10: Commit notification service work**

Run in repo root:

```powershell
git add frontend/src/types/backendNotification.ts frontend/src/adapters/notificationAdapter.ts frontend/src/adapters/notificationAdapter.test.ts frontend/src/services/backendNotificationService.ts frontend/src/services/backendNotificationService.test.ts frontend/src/services/notificationService.ts
git commit -m "feat: add backend notification service"
```

---

### Task 3: Frontend Backend Activity Log Service

**Files:**
- Create: `frontend/src/types/backendActivityLog.ts`
- Create: `frontend/src/adapters/activityLogAdapter.ts`
- Create: `frontend/src/adapters/activityLogAdapter.test.ts`
- Create: `frontend/src/services/backendActivityLogService.ts`
- Create: `frontend/src/services/backendActivityLogService.test.ts`
- Modify: `frontend/src/services/activityLogService.ts`

- [ ] **Step 1: Write failing adapter test**

Create `frontend/src/adapters/activityLogAdapter.test.ts`:

```ts
import { describe, expect, test } from 'vitest'

import { adaptBackendActivityLog } from '@/adapters/activityLogAdapter'

describe('adaptBackendActivityLog', () => {
  test('maps backend activity log to UI log', () => {
    expect(adaptBackendActivityLog({
      id: 5,
      accountId: 10,
      username: 'manager',
      action: 'APPROVE',
      entityType: 'leave_requests',
      entityId: 2,
      oldValueJson: null,
      newValueJson: '{"status":"APPROVED"}',
      createdAt: '2026-09-23T08:30:00',
      ipAddress: null,
    })).toEqual({
      id: '5',
      timestamp: '2026-09-23T08:30:00',
      actorName: 'manager',
      actorRole: 'MANAGER',
      action: 'APPROVE',
      target: 'leave_requests #2',
      description: '{"status":"APPROVED"}',
      status: 'SUCCESS',
      ipAddress: '',
    })
  })
})
```

- [ ] **Step 2: Run adapter test to verify it fails**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/activityLogAdapter.test.ts
```

Expected: FAIL because adapter/type files do not exist.

- [ ] **Step 3: Add backend activity log type and adapter**

Create `frontend/src/types/backendActivityLog.ts`:

```ts
export interface BackendActivityLogResponse {
  id: number
  accountId: number
  username: string
  action: string
  entityType: string
  entityId: number | null
  oldValueJson: string | null
  newValueJson: string | null
  createdAt: string
  ipAddress: string | null
}
```

Create `frontend/src/adapters/activityLogAdapter.ts`:

```ts
import type { BackendActivityLogResponse } from '@/types/backendActivityLog'
import type { ActivityLog } from '@/types/management'

export const adaptBackendActivityLog = (response: BackendActivityLogResponse): ActivityLog => ({
  id: String(response.id),
  timestamp: response.createdAt,
  actorName: response.username,
  actorRole: 'MANAGER',
  action: response.action,
  target: response.entityId == null ? response.entityType : `${response.entityType} #${response.entityId}`,
  description: response.newValueJson ?? response.oldValueJson ?? '',
  status: 'SUCCESS',
  ipAddress: response.ipAddress ?? '',
})
```

- [ ] **Step 4: Write failing backend activity service test**

Create `frontend/src/services/backendActivityLogService.test.ts`:

```ts
import { beforeEach, describe, expect, test, vi } from 'vitest'

import { backendActivityLogService } from '@/services/backendActivityLogService'
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

const jsonResponse = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

describe('backendActivityLogService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('lists activity logs with filters and client-side search', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
      { id: 1, accountId: 10, username: 'manager', action: 'APPROVE', entityType: 'leave_requests', entityId: 2, oldValueJson: null, newValueJson: '{"status":"APPROVED"}', createdAt: '2026-09-23T08:00:00', ipAddress: null },
      { id: 2, accountId: 10, username: 'manager', action: 'LOCK', entityType: 'employees', entityId: 3, oldValueJson: null, newValueJson: '{}', createdAt: '2026-09-23T09:00:00', ipAddress: null },
    ]))

    const result = await backendActivityLogService.list({ page: 1, pageSize: 5, search: 'leave', action: 'APPROVE', startDate: '2026-09-01', endDate: '2026-09-30' })

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/admin/activity-logs?action=APPROVE&from=2026-09-01&to=2026-09-30', expect.any(Object))
    expect(result.total).toBe(1)
    expect(result.data[0]?.target).toBe('leave_requests #2')
  })
})
```

- [ ] **Step 5: Run tests to verify they fail**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/activityLogAdapter.test.ts src/services/backendActivityLogService.test.ts
```

Expected: adapter may pass after Step 3, service fails because `backendActivityLogService` does not exist.

- [ ] **Step 6: Add backend activity log service**

Create `frontend/src/services/backendActivityLogService.ts`:

```ts
import { adaptBackendActivityLog } from '@/adapters/activityLogAdapter'
import { requestJson } from '@/services/apiClient'
import type { BackendActivityLogResponse } from '@/types/backendActivityLog'
import type { ActivityLog, PaginatedResult } from '@/types/management'

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  action?: string
  startDate?: string
  endDate?: string
}

const paginate = (items: ActivityLog[], page: number, pageSize: number): PaginatedResult<ActivityLog> => {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const startIndex = (safePage - 1) * pageSize
  return { data: items.slice(startIndex, startIndex + pageSize), page: safePage, pageSize, total, totalPages }
}

export const backendActivityLogService = {
  async list({ page = 1, pageSize = 5, search = '', action = 'ALL', startDate = '', endDate = '' }: ListParams = {}) {
    const params = new URLSearchParams()
    if (action !== 'ALL') params.set('action', action)
    if (startDate) params.set('from', startDate)
    if (endDate) params.set('to', endDate)
    const query = params.toString()
    const response = await requestJson<BackendActivityLogResponse[]>(`/api/admin/activity-logs${query ? `?${query}` : ''}`)
    const normalized = search.trim().toLowerCase()
    const items = response.map(adaptBackendActivityLog).filter((log) => {
      if (!normalized) return true
      return [log.actorName, log.target, log.description, log.action].some((value) => value.toLowerCase().includes(normalized))
    })
    return paginate(items, page, pageSize)
  },
}
```

- [ ] **Step 7: Update activity log facade**

Modify `frontend/src/services/activityLogService.ts` imports:

```ts
import { apiConfig } from '@/config/api'
import { activityLogSeed } from '@/mocks/managementData'
import { backendActivityLogService } from '@/services/backendActivityLogService'
import type { ActivityLog, PaginatedResult } from '@/types/management'
```

At the start of `list()` add:

```ts
if (!apiConfig.useMockApi) {
  return backendActivityLogService.list({ page, pageSize, search, action, startDate, endDate })
}
```

- [ ] **Step 8: Run focused tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/activityLogAdapter.test.ts src/services/backendActivityLogService.test.ts
```

Expected: PASS.

- [ ] **Step 9: Commit activity log service work**

Run in repo root:

```powershell
git add frontend/src/types/backendActivityLog.ts frontend/src/adapters/activityLogAdapter.ts frontend/src/adapters/activityLogAdapter.test.ts frontend/src/services/backendActivityLogService.ts frontend/src/services/backendActivityLogService.test.ts frontend/src/services/activityLogService.ts
git commit -m "feat: add backend activity log service"
```

---

### Task 4: Navigation Summary Frontend and Real Sidebar Badges

**Files:**
- Create: `frontend/src/types/navigationSummary.ts`
- Create: `frontend/src/services/navigationSummaryService.ts`
- Create: `frontend/src/services/navigationSummaryService.test.ts`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`

- [ ] **Step 1: Write failing summary service tests**

Create `frontend/src/services/navigationSummaryService.test.ts`:

```ts
import { beforeEach, describe, expect, test, vi } from 'vitest'

import { navigationSummaryService } from '@/services/navigationSummaryService'
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

describe('navigationSummaryService', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('localStorage', createStorage())
    vi.stubGlobal('sessionStorage', createStorage())
    sessionStorage.setItem(AUTH_STORAGE_KEYS.TOKEN, 'token')
    vi.stubGlobal('fetch', vi.fn())
  })

  test('loads backend navigation summary counts', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({
      pendingLeaveRequests: 1,
      pendingPreferences: 2,
      pendingSwapRequests: 3,
      unreadNotifications: 4,
      pendingSchedules: 0,
    }), { headers: { 'Content-Type': 'application/json' } }))

    const result = await navigationSummaryService.get()

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/me/navigation-summary', expect.any(Object))
    expect(result.unreadNotifications).toBe(4)
  })
})
```

- [ ] **Step 2: Run summary service test to verify it fails**

Run in `frontend/`:

```powershell
npm.cmd test -- src/services/navigationSummaryService.test.ts
```

Expected: FAIL because summary service/type do not exist.

- [ ] **Step 3: Add navigation summary type and service**

Create `frontend/src/types/navigationSummary.ts`:

```ts
export interface NavigationSummary {
  pendingLeaveRequests: number
  pendingPreferences: number
  pendingSwapRequests: number
  unreadNotifications: number
  pendingSchedules: number
}
```

Create `frontend/src/services/navigationSummaryService.ts`:

```ts
import { apiConfig } from '@/config/api'
import { leaveRequestSeed, notificationSeed, preferenceSeed, swapRequestSeed } from '@/mocks/managementData'
import { scheduleSeed } from '@/mocks/schedules'
import { requestJson } from '@/services/apiClient'
import type { NavigationSummary } from '@/types/navigationSummary'

const mockSummary = (): NavigationSummary => ({
  pendingLeaveRequests: leaveRequestSeed.filter((item) => item.status === 'PENDING').length,
  pendingPreferences: preferenceSeed.filter((item) => item.status === 'PENDING').length,
  pendingSwapRequests: swapRequestSeed.filter((item) => item.status === 'PENDING_TARGET' || item.status === 'PENDING_APPROVAL').length,
  unreadNotifications: notificationSeed.filter((item) => !item.isRead).length,
  pendingSchedules: scheduleSeed.filter((item) => item.status === 'PENDING_APPROVAL').length,
})

export const navigationSummaryService = {
  async get(): Promise<NavigationSummary> {
    if (apiConfig.useMockApi) {
      return mockSummary()
    }
    return requestJson<NavigationSummary>('/api/me/navigation-summary')
  },
}
```

- [ ] **Step 4: Write failing Sidebar tests for backend counts**

Modify `frontend/src/components/layout/Sidebar.test.tsx` to use React Testing Library async rendering if available. Add tests:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
```

Add inside `describe('Sidebar')`:

```tsx
it('shows backend summary badges when counts are positive', async () => {
  vi.stubEnv('VITE_USE_MOCK_API', 'false')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({
    pendingLeaveRequests: 1,
    pendingPreferences: 2,
    pendingSwapRequests: 3,
    unreadNotifications: 4,
    pendingSchedules: 0,
  }), { headers: { 'Content-Type': 'application/json' } })))
  vi.stubGlobal('localStorage', { getItem: () => 'token', setItem: () => undefined, removeItem: () => undefined, clear: () => undefined, key: () => null, length: 0 })
  vi.stubGlobal('sessionStorage', { getItem: () => 'token', setItem: () => undefined, removeItem: () => undefined, clear: () => undefined, key: () => null, length: 0 })

  const { Sidebar } = await import('./Sidebar')
  render(
    <MemoryRouter>
      <Sidebar items={items} user={managerUser} isMobileOpen={false} onMobileToggle={() => undefined} onLogout={() => undefined} />
    </MemoryRouter>,
  )

  await waitFor(() => expect(screen.getByText('4')).toBeTruthy())
  expect(screen.getByText('1')).toBeTruthy()
  expect(screen.getByText('2')).toBeTruthy()
  expect(screen.getByText('3')).toBeTruthy()
})
```

- [ ] **Step 5: Run Sidebar tests to verify they fail**

Run in `frontend/`:

```powershell
npm.cmd test -- src/components/layout/Sidebar.test.tsx
```

Expected: FAIL because Sidebar still returns `0` in backend mode and does not load summary counts.

- [ ] **Step 6: Update Sidebar to use summary service**

Modify imports in `Sidebar.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { navigationSummaryService } from '@/services/navigationSummaryService'
import type { NavigationSummary } from '@/types/navigationSummary'
```

Remove direct mock seed imports and `apiConfig` import from `Sidebar.tsx`.

Replace `getBadgeCount` with:

```tsx
const emptySummary: NavigationSummary = {
  pendingLeaveRequests: 0,
  pendingPreferences: 0,
  pendingSwapRequests: 0,
  unreadNotifications: 0,
  pendingSchedules: 0,
}

const getBadgeCount = (label: string, role: Role, summary: NavigationSummary) => {
  if (label === 'Ngày nghỉ') return summary.pendingLeaveRequests
  if (label === 'Nguyện vọng') return summary.pendingPreferences
  if (label === 'Yêu cầu đổi ca') return summary.pendingSwapRequests
  if (label === 'Thông báo') return summary.unreadNotifications
  if (role === Role.MANAGER && label === 'Quản lý lịch') return summary.pendingSchedules
  return 0
}
```

Inside `Sidebar` before `navContent`, add:

```tsx
const [summary, setSummary] = useState<NavigationSummary>(emptySummary)

useEffect(() => {
  let mounted = true
  navigationSummaryService.get()
    .then((result) => {
      if (mounted) setSummary(result)
    })
    .catch(() => {
      if (mounted) setSummary(emptySummary)
    })
  return () => {
    mounted = false
  }
}, [user.role])
```

In render, compute count once:

```tsx
const badgeCount = getBadgeCount(item.label, user.role, summary)
```

Render:

```tsx
{badgeCount > 0 && <span className="min-w-5 rounded-full bg-amber-100 px-1.5 py-0.5 text-center text-[11px] font-semibold text-amber-700">{badgeCount}</span>}
```

- [ ] **Step 7: Run summary and Sidebar tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/services/navigationSummaryService.test.ts src/components/layout/Sidebar.test.tsx
```

Expected: PASS.

- [ ] **Step 8: Commit summary and Sidebar work**

Run in repo root:

```powershell
git add frontend/src/types/navigationSummary.ts frontend/src/services/navigationSummaryService.ts frontend/src/services/navigationSummaryService.test.ts frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git commit -m "feat: show real navigation badges"
```

---

### Task 5: Wire Notification Pages

**Files:**
- Modify: `frontend/src/pages/manager/NotificationsPage.tsx`
- Modify: `frontend/src/pages/employee/EmployeeNotificationsPage.tsx`
- Test: page test files if existing test setup supports page rendering; otherwise cover behavior with service/sidebar tests and verify by build.

- [ ] **Step 1: Update manager notifications page backend-mode create-panel behavior**

In `NotificationsPage.tsx`, import `apiConfig`:

```tsx
import { apiConfig } from '@/config/api'
```

Replace `PageHeader` description with backend-aware text:

```tsx
<PageHeader
  title="Quản lý thông báo"
  description={apiConfig.useMockApi ? 'Theo dõi và tạo thông báo trong mock state.' : 'Theo dõi thông báo phát sinh từ các nghiệp vụ backend.'}
/>
```

Wrap the create panel so it only renders in mock mode:

```tsx
{apiConfig.useMockApi && (
  <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-4">
    ...existing create notification panel...
  </div>
)}
```

Keep `handleCreate` unchanged because it is now only reachable in mock mode.

- [ ] **Step 2: Update employee notifications page to use service**

Modify imports in `EmployeeNotificationsPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Pagination } from '@/components/common/Pagination'
import { notificationService } from '@/services/notificationService'
import type { NotificationItem, PaginatedResult } from '@/types/management'
```

Remove the local `notifications` array. Add state:

```tsx
const [page, setPage] = useState(1)
const [loading, setLoading] = useState(true)
const [error, setError] = useState('')
const [data, setData] = useState<PaginatedResult<NotificationItem>>({ data: [], page: 1, pageSize: 5, total: 0, totalPages: 1 })
const [selected, setSelected] = useState<NotificationItem | null>(null)
```

Add fetch function:

```tsx
const fetchData = async () => {
  setLoading(true)
  setError('')
  try {
    const result = await notificationService.list({ page, pageSize: 5, search, status: statusFilter })
    setData(result)
    const selectedId = searchParams.get('notificationId')
    setSelected(selectedId ? result.data.find((item) => item.id === selectedId) ?? null : null)
  } catch (loadError) {
    setError(loadError instanceof Error ? loadError.message : 'Không thể tải thông báo.')
  } finally {
    setLoading(false)
  }
}
```

Add effect:

```tsx
useEffect(() => {
  let mounted = true
  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const result = await notificationService.list({ page, pageSize: 5, search, status: statusFilter })
      if (mounted) {
        setData(result)
        const selectedId = searchParams.get('notificationId')
        setSelected(selectedId ? result.data.find((item) => item.id === selectedId) ?? null : null)
      }
    } catch (loadError) {
      if (mounted) setError(loadError instanceof Error ? loadError.message : 'Không thể tải thông báo.')
    } finally {
      if (mounted) setLoading(false)
    }
  }
  void load()
  return () => { mounted = false }
}, [page, search, statusFilter, searchParams])
```

Replace stats source:

```tsx
const stats = useMemo(() => ({
  total: data.total,
  unread: data.data.filter((item) => !item.isRead).length,
  read: data.data.filter((item) => item.isRead).length,
}), [data])
```

Update `handleMarkRead`:

```tsx
const handleMarkRead = async (id: string) => {
  try {
    await notificationService.markRead(id)
    closeDetails()
    await fetchData()
  } catch (markError) {
    window.alert(markError instanceof Error ? markError.message : 'Không thể đánh dấu đã đọc.')
  }
}
```

Render table state:

```tsx
{loading ? (
  <LoadingState label="Đang tải thông báo..." />
) : error ? (
  <ErrorState message={error} onRetry={fetchData} />
) : data.data.length === 0 ? (
  <EmptyState title="Không có thông báo" description="Chưa có thông báo phù hợp với bộ lọc." />
) : (
  <>
    <DataTable data={data.data} columns={[...]} />
    <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
  </>
)}
```

- [ ] **Step 3: Run TypeScript build to catch page wiring errors**

Run in `frontend/`:

```powershell
npm.cmd run build
```

Expected: PASS. If TypeScript reports exact line errors in `EmployeeNotificationsPage.tsx`, fix only those wiring errors.

- [ ] **Step 4: Run focused frontend tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/notificationAdapter.test.ts src/services/backendNotificationService.test.ts src/components/layout/Sidebar.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Commit notification page wiring**

Run in repo root:

```powershell
git add frontend/src/pages/manager/NotificationsPage.tsx frontend/src/pages/employee/EmployeeNotificationsPage.tsx
git commit -m "feat: connect notification pages to backend"
```

---

### Task 6: Final Verification and Push

**Files:**
- No new source files unless verification finds a scoped bug.

- [ ] **Step 1: Run backend focused tests**

Run in `backend/`:

```powershell
./mvnw.cmd "-Dtest=NavigationSummaryServiceTest,NavigationSummaryControllerTest" test
```

Expected: PASS.

- [ ] **Step 2: Run backend full suite**

Run in `backend/`:

```powershell
./mvnw.cmd test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 3: Run backend package**

Run in `backend/`:

```powershell
./mvnw.cmd -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run frontend focused tests**

Run in `frontend/`:

```powershell
npm.cmd test -- src/adapters/notificationAdapter.test.ts src/services/backendNotificationService.test.ts src/adapters/activityLogAdapter.test.ts src/services/backendActivityLogService.test.ts src/services/navigationSummaryService.test.ts src/components/layout/Sidebar.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Run frontend full tests, lint, and build**

Run in `frontend/`:

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Expected: tests PASS, lint has no errors, build PASS. Existing chunk-size warning is acceptable. Existing `AuthContext` Fast Refresh warning is acceptable if unchanged.

- [ ] **Step 6: Review final diff and status**

Run in repo root:

```powershell
git status --short
git diff --stat main...HEAD
git log --oneline main..HEAD
```

Expected: only Phase 9I and Phase 9J intended files plus the already-known untracked docs remain. Do not add untracked `docs/quy_dinh_ca_lam_viec.md` or `docs/Đặc tả_Phát.txt`.

- [ ] **Step 7: Push branch**

Run in repo root:

```powershell
git push
```

Expected: branch push succeeds. Do not create PR unless explicitly requested.

---

## Plan Self-Review

- Spec coverage: backend summary counts are Task 1; notification backend wiring is Task 2 and Task 5; activity logs are Task 3; real Sidebar badges are Task 4; hiding create notification in backend mode is Task 5; verification is Task 6.
- Placeholder scan: no `TBD`, no undefined follow-up tasks, and no intentional unspecified implementation areas remain.
- Type consistency: `NavigationSummary`, `BackendNotificationResponse`, `BackendActivityLogResponse`, service method names, and adapter names are defined before use and reused consistently.

# Phase 7 Notification & Activity Logs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-first in-app notifications and manager activity logs, then wire them into key schedule/leave/swap/admin flows.

**Architecture:** Add focused repositories, DTOs, services, and controllers for notifications and activity logs. Existing business services call these services inside their current transactions after successful state changes. Frontend integration, realtime delivery, email delivery, metadata/deep-links, export, and UI work remain out of scope.

**Tech Stack:** Java 21, Spring Boot, Spring Security method security, Spring Data JPA, Hibernate JSONB mapping patterns, JUnit, Mockito, AssertJ, PostgreSQL-backed integration tests where repository behavior matters.

---

## File Structure

### New Backend Files

- `backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java`
  - Queries notification inbox data by recipient and read state.
  - Finds notification by id + recipient id for ownership checks.
  - Bulk marks all notifications for one recipient as read.

- `backend/src/main/java/scheduling/scheduling/repository/ActivityLogRepository.java`
  - Searches activity logs newest-first with optional filters.

- `backend/src/main/java/scheduling/scheduling/notification/NotificationService.java`
  - Creates notifications, lists current account notifications, marks read/all-read.

- `backend/src/main/java/scheduling/scheduling/notification/NotificationResponse.java`
  - API DTO for notification inbox rows.

- `backend/src/main/java/scheduling/scheduling/notification/EmployeeNotificationController.java`
  - `GET /api/employee/notifications`
  - `PATCH /api/employee/notifications/{notificationId}/read`
  - `PATCH /api/employee/notifications/read-all`

- `backend/src/main/java/scheduling/scheduling/audit/ActivityLogService.java`
  - Records compact JSON snapshots and lists logs for managers.

- `backend/src/main/java/scheduling/scheduling/audit/ActivityLogResponse.java`
  - API DTO for manager audit log list.

- `backend/src/main/java/scheduling/scheduling/audit/AdminActivityLogController.java`
  - `GET /api/admin/activity-logs`

### Existing Backend Files To Modify

- `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
  - Record employee create/update/lock/unlock logs.

- `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java`
  - Record leave approve/reject logs and notify requesting employee.

- `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`
  - Record optimize terminal outcome logs.

- `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
  - Record assignment create/update/delete/lock/unlock logs.

- `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
  - Record schedule publish log and notify employees with assignments in the period.

- `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java`
  - Notify target employee when swap is requested.
  - Notify requester when target accepts/rejects.

- `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java`
  - Record swap approve/reject logs and notify both related employees.

- `docs/project_plan.md`
  - Mark completed Phase 7 items only after implementation and verification.

### New Tests

- `backend/src/test/java/scheduling/scheduling/notification/NotificationServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/notification/EmployeeNotificationControllerTest.java`
- `backend/src/test/java/scheduling/scheduling/audit/ActivityLogServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/audit/AdminActivityLogControllerTest.java`

### Existing Tests To Extend

- `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`
- `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`

---

## Constants And Conventions

Use these string constants consistently in services/tests.

```java
// Notification types
SCHEDULE_PUBLISHED
LEAVE_APPROVED
LEAVE_REJECTED
SWAP_REQUESTED
SWAP_TARGET_ACCEPTED
SWAP_TARGET_REJECTED
SWAP_APPROVED
SWAP_REJECTED

// Activity actions
CREATE
UPDATE
DELETE
LOCK
UNLOCK
APPROVE
REJECT
PUBLISH
OPTIMIZE

// Entity types
employees
leave_requests
optimization_runs
shift_assignments
schedule_periods
swap_requests
```

---

### Task 1: Notification Repository, DTO, Service

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/notification/NotificationResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/notification/NotificationService.java`
- Create: `backend/src/test/java/scheduling/scheduling/notification/NotificationServiceTest.java`

- [ ] **Step 1: Write failing notification service tests**

Create `NotificationServiceTest.java` with tests for own-list filtering, mark one read, mark all read, and ownership protection. Use Mockito for repository/account lookup. The test should expect 404 when a notification id does not belong to the principal.

```java
package scheduling.scheduling.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Notification;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.NotificationRepository;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final NotificationService service = new NotificationService(notificationRepository, accountRepository);

    @Test
    void listsOnlyCurrentAccountNotificationsWithReadFilter() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        Notification notification = notification(100L, account, "LEAVE_APPROVED", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findForRecipient(10L, false)).thenReturn(List.of(notification));

        List<NotificationResponse> result = service.list(principal(10L, AccountRole.EMPLOYEE), false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).notificationType()).isEqualTo("LEAVE_APPROVED");
        assertThat(result.get(0).read()).isFalse();
    }

    @Test
    void marksOwnNotificationRead() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        Notification notification = notification(100L, account, "SWAP_REQUESTED", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findByIdAndRecipientId(100L, 10L)).thenReturn(Optional.of(notification));

        NotificationResponse result = service.markRead(principal(10L, AccountRole.EMPLOYEE), 100L);

        assertThat(notification.getRead()).isTrue();
        assertThat(result.read()).isTrue();
    }

    @Test
    void rejectsMarkingAnotherUsersNotification() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findByIdAndRecipientId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(principal(10L, AccountRole.EMPLOYEE), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void marksAllOwnNotificationsRead() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        service.markAllRead(principal(10L, AccountRole.EMPLOYEE));

        verify(notificationRepository).markAllRead(10L);
    }

    private static CurrentAccountPrincipal principal(Long accountId, AccountRole role) {
        return new CurrentAccountPrincipal(accountId, "user" + accountId, role);
    }

    private static Account account(Long id, AccountRole role) {
        Account account = Account.builder().username("user" + id).passwordHash("hash").role(role).build();
        account.setId(id);
        return account;
    }

    private static Notification notification(Long id, Account recipient, String type, boolean read) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .title("Title")
                .content("Content")
                .notificationType(type)
                .read(read)
                .build();
        notification.setId(id);
        notification.setCreatedAt(LocalDateTime.of(2026, 9, 20, 9, 0));
        return notification;
    }
}
```

- [ ] **Step 2: Run notification service test and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=NotificationServiceTest test`

Expected: compilation fails because `NotificationRepository`, `NotificationService`, and `NotificationResponse` do not exist.

- [ ] **Step 3: Add notification repository**

Create `NotificationRepository.java`.

```java
package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n
            from Notification n
            join fetch n.recipient r
            where r.id = :recipientId
              and (:read is null or n.read = :read)
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findForRecipient(@Param("recipientId") Long recipientId, @Param("read") Boolean read);

    @Query("""
            select n
            from Notification n
            join fetch n.recipient r
            where n.id = :id
              and r.id = :recipientId
            """)
    Optional<Notification> findByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true
            where n.recipient.id = :recipientId
              and n.read = false
            """)
    int markAllRead(@Param("recipientId") Long recipientId);
}
```

- [ ] **Step 4: Add notification response DTO**

Create `NotificationResponse.java`.

```java
package scheduling.scheduling.notification;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.Notification;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        String notificationType,
        Boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getNotificationType(),
                notification.getRead(),
                notification.getCreatedAt());
    }
}
```

- [ ] **Step 5: Add notification service**

Create `NotificationService.java`.

```java
package scheduling.scheduling.notification;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Notification;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;

    public NotificationService(NotificationRepository notificationRepository, AccountRepository accountRepository) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void notify(Account recipient, String type, String title, String content) {
        if (recipient == null || recipient.getId() == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .notificationType(requireText(type, "Notification type is required"))
                .title(requireText(title, "Notification title is required"))
                .content(StringUtils.hasText(content) ? content.trim() : null)
                .read(false)
                .build());
    }

    @Transactional
    public void notifyAll(Collection<Account> recipients, String type, String title, String content) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        Map<Long, Account> uniqueRecipients = new LinkedHashMap<>();
        for (Account recipient : recipients) {
            if (recipient != null && recipient.getId() != null) {
                uniqueRecipients.putIfAbsent(recipient.getId(), recipient);
            }
        }
        uniqueRecipients.values().forEach(recipient -> notify(recipient, type, title, content));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(CurrentAccountPrincipal principal, Boolean read) {
        Account account = requireAccount(principal);
        return notificationRepository.findForRecipient(account.getId(), read).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(CurrentAccountPrincipal principal, Long notificationId) {
        Account account = requireAccount(principal);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(CurrentAccountPrincipal principal) {
        Account account = requireAccount(principal);
        notificationRepository.markAllRead(account.getId());
    }

    private Account requireAccount(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        return accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required"));
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
```

- [ ] **Step 6: Run notification service test and verify GREEN**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=NotificationServiceTest test`

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit notification service foundation**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/NotificationRepository.java backend/src/main/java/scheduling/scheduling/notification/NotificationResponse.java backend/src/main/java/scheduling/scheduling/notification/NotificationService.java backend/src/test/java/scheduling/scheduling/notification/NotificationServiceTest.java
```

---

### Task 2: Notification API Controller

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/notification/EmployeeNotificationController.java`
- Create: `backend/src/test/java/scheduling/scheduling/notification/EmployeeNotificationControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Use the existing controller-test style in `EmployeeLeaveRequestControllerTest`: `@WebMvcTest`, import security config/filter/exception handler, mock `JwtService`, and send a bearer token.

```java
package scheduling.scheduling.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeNotificationController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void listsNotifications() throws Exception {
        givenToken("employee-token", principal());
        when(notificationService.list(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class), eq(false))).thenReturn(List.of(new NotificationResponse(
                1L, "Title", "Content", "LEAVE_APPROVED", false, LocalDateTime.of(2026, 9, 20, 9, 0))));

        mockMvc.perform(get("/api/employee/notifications").param("read", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].notificationType").value("LEAVE_APPROVED"));
    }

    @Test
    void marksNotificationRead() throws Exception {
        givenToken("employee-token", principal());
        when(notificationService.markRead(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class), eq(1L))).thenReturn(new NotificationResponse(
                1L, "Title", "Content", "LEAVE_APPROVED", true, LocalDateTime.of(2026, 9, 20, 9, 0)));

        mockMvc.perform(patch("/api/employee/notifications/1/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void marksAllNotificationsRead() throws Exception {
        givenToken("employee-token", principal());

        mockMvc.perform(patch("/api/employee/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(org.mockito.ArgumentMatchers.any(CurrentAccountPrincipal.class));
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal principal() {
        return new CurrentAccountPrincipal(1L, "employee", AccountRole.EMPLOYEE);
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }
}
```

- [ ] **Step 2: Run controller test and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=EmployeeNotificationControllerTest test`

Expected: compilation fails because `EmployeeNotificationController` does not exist.

- [ ] **Step 3: Add notification controller**

Create `EmployeeNotificationController.java`.

```java
package scheduling.scheduling.notification;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/employee/notifications")
@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
public class EmployeeNotificationController {

    private final NotificationService notificationService;

    public EmployeeNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    ResponseEntity<List<NotificationResponse>> listNotifications(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) Boolean read) {
        return ResponseEntity.ok(notificationService.list(principal, read));
    }

    @PatchMapping("/{notificationId}/read")
    ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(principal, notificationId));
    }

    @PatchMapping("/read-all")
    ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CurrentAccountPrincipal principal) {
        notificationService.markAllRead(principal);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run notification controller test and focused service test**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=NotificationServiceTest,EmployeeNotificationControllerTest test`

Expected: both test classes pass.

- [ ] **Step 5: Commit notification API**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/notification/EmployeeNotificationController.java backend/src/test/java/scheduling/scheduling/notification/EmployeeNotificationControllerTest.java
```

---

### Task 3: Activity Log Repository, DTO, Service, Controller

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/repository/ActivityLogRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/audit/ActivityLogResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/audit/ActivityLogService.java`
- Create: `backend/src/main/java/scheduling/scheduling/audit/AdminActivityLogController.java`
- Create: `backend/src/test/java/scheduling/scheduling/audit/ActivityLogServiceTest.java`
- Create: `backend/src/test/java/scheduling/scheduling/audit/AdminActivityLogControllerTest.java`

- [ ] **Step 1: Write failing activity log service tests**

Create tests that verify `record` serializes compact snapshots and manager list delegates to repository filters.

```java
package scheduling.scheduling.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.ActivityLog;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ActivityLogRepository;

class ActivityLogServiceTest {

    private final ActivityLogRepository activityLogRepository = mock(ActivityLogRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ActivityLogService service = new ActivityLogService(activityLogRepository, accountRepository, new ObjectMapper());

    @Test
    void recordsActivityLogWithJsonSnapshots() throws Exception {
        Account manager = account(1L, AccountRole.MANAGER);

        service.record(manager, "UPDATE", "employees", 10L, Map.of("status", "ACTIVE"), Map.of("status", "INACTIVE"));

        verify(activityLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getAccount().getId().equals(1L)
                        && log.getAction().equals("UPDATE")
                        && log.getEntityType().equals("employees")
                        && log.getEntityId().equals(10L)
                        && log.getOldValueJson().contains("ACTIVE")
                        && log.getNewValueJson().contains("INACTIVE")));
    }

    @Test
    void managerListsActivityLogsWithFilters() {
        Account manager = account(1L, AccountRole.MANAGER);
        ActivityLog log = ActivityLog.builder()
                .account(manager)
                .action("PUBLISH")
                .entityType("schedule_periods")
                .entityId(5L)
                .build();
        log.setId(99L);
        log.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(activityLogRepository.search("PUBLISH", "schedule_periods", 1L,
                LocalDate.of(2026, 9, 1).atStartOfDay(), LocalDate.of(2026, 9, 30).plusDays(1).atStartOfDay()))
                .thenReturn(java.util.List.of(log));

        var result = service.list(principal(), "PUBLISH", "schedule_periods", 1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo("PUBLISH");
    }

    private static CurrentAccountPrincipal principal() {
        return new CurrentAccountPrincipal(1L, "manager", AccountRole.MANAGER);
    }

    private static Account account(Long id, AccountRole role) {
        Account account = Account.builder().username("user" + id).passwordHash("hash").role(role).build();
        account.setId(id);
        return account;
    }
}
```

- [ ] **Step 2: Run activity log service test and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=ActivityLogServiceTest test`

Expected: compilation fails because activity log repository/service/response do not exist.

- [ ] **Step 3: Add activity log repository**

Create `ActivityLogRepository.java`.

```java
package scheduling.scheduling.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query("""
            select al
            from ActivityLog al
            join fetch al.account a
            where (:action is null or al.action = :action)
              and (:entityType is null or al.entityType = :entityType)
              and (:accountId is null or a.id = :accountId)
              and (:from is null or al.createdAt >= :from)
              and (:toExclusive is null or al.createdAt < :toExclusive)
            order by al.createdAt desc, al.id desc
            """)
    List<ActivityLog> search(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("accountId") Long accountId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);
}
```

- [ ] **Step 4: Add activity log response DTO and service**

Create `ActivityLogResponse.java` and `ActivityLogService.java`.

```java
package scheduling.scheduling.audit;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.ActivityLog;

public record ActivityLogResponse(
        Long id,
        Long accountId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String oldValueJson,
        String newValueJson,
        LocalDateTime createdAt,
        String ipAddress) {

    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getAccount().getId(),
                log.getAccount().getUsername(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValueJson(),
                log.getNewValueJson(),
                log.getCreatedAt(),
                log.getIpAddress());
    }
}
```

```java
package scheduling.scheduling.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.ActivityLog;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ActivityLogRepository;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public ActivityLogService(ActivityLogRepository activityLogRepository, AccountRepository accountRepository, ObjectMapper objectMapper) {
        this.activityLogRepository = activityLogRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Account actor, String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        if (actor == null || actor.getId() == null) {
            return;
        }
        activityLogRepository.save(ActivityLog.builder()
                .account(actor)
                .action(requireText(action, "Action is required"))
                .entityType(requireText(entityType, "Entity type is required"))
                .entityId(entityId)
                .oldValueJson(writeNullable(oldValue))
                .newValueJson(writeNullable(newValue))
                .build());
    }

    @Transactional(readOnly = true)
    public List<ActivityLogResponse> list(
            CurrentAccountPrincipal principal,
            String action,
            String entityType,
            Long accountId,
            LocalDate from,
            LocalDate to) {
        requireManager(principal);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid activity log filter");
        }
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        String normalizedAction = StringUtils.hasText(action) ? action.trim().toUpperCase(java.util.Locale.ROOT) : null;
        String normalizedEntityType = StringUtils.hasText(entityType) ? entityType.trim() : null;
        return activityLogRepository.search(normalizedAction, normalizedEntityType, accountId, fromDateTime, toExclusive).stream()
                .map(ActivityLogResponse::from)
                .toList();
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER && account.getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private String writeNullable(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize activity log snapshot", ex);
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
```

- [ ] **Step 5: Add activity log controller and controller test**

Create `AdminActivityLogController.java`.

```java
package scheduling.scheduling.audit;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/activity-logs")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminActivityLogController {

    private final ActivityLogService activityLogService;

    public AdminActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    ResponseEntity<List<ActivityLogResponse>> listActivityLogs(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(activityLogService.list(principal, action, entityType, accountId, from, to));
    }
}
```

Create `AdminActivityLogControllerTest.java` using the same security setup as `EmployeeLeaveRequestControllerTest`.

```java
package scheduling.scheduling.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminActivityLogController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminActivityLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityLogService activityLogService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsActivityLogs() throws Exception {
        givenToken("manager-token", manager());
        when(activityLogService.list(any(CurrentAccountPrincipal.class), eq("PUBLISH"), eq("schedule_periods"), eq(1L),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(new ActivityLogResponse(
                        9L, 1L, "manager01", "PUBLISH", "schedule_periods", 5L, null,
                        "{\"status\":\"PUBLISHED\"}", LocalDateTime.of(2026, 9, 20, 10, 0), null)));

        mockMvc.perform(get("/api/admin/activity-logs")
                        .param("action", "PUBLISH")
                        .param("entityType", "schedule_periods")
                        .param("accountId", "1")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].action").value("PUBLISH"));
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }
}
```

- [ ] **Step 6: Run activity log focused tests**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=ActivityLogServiceTest,AdminActivityLogControllerTest test`

Expected: both test classes pass.

- [ ] **Step 7: Commit activity log APIs**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/ActivityLogRepository.java backend/src/main/java/scheduling/scheduling/audit backend/src/test/java/scheduling/scheduling/audit
```

---

### Task 4: Wire Leave Review Notifications And Logs

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java`

- [ ] **Step 1: Add failing tests for approve/reject events**

In `AdminLeaveRequestServiceTest`, add mocks for `NotificationService` and `ActivityLogService`. Assert approve creates `LEAVE_APPROVED` notification for `leaveRequest.employee.account` and records an `APPROVE` log for `leave_requests`. Assert reject creates `LEAVE_REJECTED` and `REJECT`.

Expected verification shape:

```java
verify(notificationService).notify(
        eq(employeeAccount),
        eq("LEAVE_APPROVED"),
        eq("Đơn nghỉ của bạn đã được duyệt"),
        contains("Đơn nghỉ từ"));
verify(activityLogService).record(
        eq(managerAccount),
        eq("APPROVE"),
        eq("leave_requests"),
        eq(leaveRequest.getId()),
        any(),
        any());
```

- [ ] **Step 2: Run leave service tests and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminLeaveRequestServiceTest test`

Expected: tests fail because services are not injected/called.

- [ ] **Step 3: Modify `AdminLeaveRequestService` constructor and review methods**

Add dependencies:

```java
private final NotificationService notificationService;
private final ActivityLogService activityLogService;
```

After `applyReview(...)` in approve/reject, call helpers:

```java
private void recordLeaveReview(Account manager, LeaveRequest leaveRequest, String action) {
    activityLogService.record(manager, action, "leave_requests", leaveRequest.getId(), null, Map.of(
            "id", leaveRequest.getId(),
            "employeeId", leaveRequest.getEmployee().getId(),
            "status", leaveRequest.getStatus(),
            "startDate", leaveRequest.getStartDate(),
            "endDate", leaveRequest.getEndDate()));
}

private void notifyLeaveReview(LeaveRequest leaveRequest, String type, String title) {
    Account recipient = leaveRequest.getEmployee().getAccount();
    notificationService.notify(recipient, type, title,
            "Đơn nghỉ từ " + leaveRequest.getStartDate() + " đến " + leaveRequest.getEndDate()
                    + ("APPROVED".equals(leaveRequest.getStatus()) ? " đã được duyệt." : " đã bị từ chối."));
}
```

Use `APPROVE`/`REJECT` for actions and `LEAVE_APPROVED`/`LEAVE_REJECTED` for notification types.

- [ ] **Step 4: Run leave service tests**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminLeaveRequestServiceTest test`

Expected: pass.

- [ ] **Step 5: Commit leave events**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java
```

---

### Task 5: Wire Schedule Publish Notifications And Logs

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Add failing publish-event tests**

Extend `AdminSchedulePeriodServiceTest` to verify publishing a period records a `PUBLISH` log and creates one `SCHEDULE_PUBLISHED` notification per unique assigned employee account. Use duplicate assignments for one account to prove `notifyAll` duplicate protection or unique recipient collection.

Verification shape:

```java
verify(activityLogService).record(eq(manager), eq("PUBLISH"), eq("schedule_periods"), eq(period.getId()), any(), any());
verify(notificationService).notifyAll(
        argThat(recipients -> recipients.size() == 2),
        eq("SCHEDULE_PUBLISHED"),
        eq("Lịch làm việc mới đã được công bố"),
        contains(period.getPeriodName()));
```

- [ ] **Step 2: Run schedule period tests and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminSchedulePeriodServiceTest test`

Expected: fails because publish does not log/notify.

- [ ] **Step 3: Modify publish service**

Inject `NotificationService` and `ActivityLogService`. In `publishSchedulePeriod`, keep `Account manager = requireManager(principal)` instead of discarding the return value. After status changes to `PUBLISHED`, collect recipients from `shiftAssignmentRepository.listForPeriod(periodId)`:

```java
List<Account> recipients = shiftAssignmentRepository.listForPeriod(periodId).stream()
        .map(assignment -> assignment.getEmployee().getAccount())
        .filter(java.util.Objects::nonNull)
        .toList();
notificationService.notifyAll(recipients, "SCHEDULE_PUBLISHED", "Lịch làm việc mới đã được công bố",
        "Lịch làm việc kỳ \"" + period.getPeriodName() + "\" đã được công bố. Vui lòng kiểm tra lịch cá nhân.");
activityLogService.record(manager, "PUBLISH", "schedule_periods", period.getId(), null, Map.of(
        "id", period.getId(),
        "periodName", period.getPeriodName(),
        "status", period.getStatus()));
```

- [ ] **Step 4: Run schedule period tests**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminSchedulePeriodServiceTest test`

Expected: pass.

- [ ] **Step 5: Commit schedule publish events**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
```

---

### Task 6: Wire Swap Notifications And Logs

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java`

- [ ] **Step 1: Add failing employee swap notification tests**

In `EmployeeSwapRequestServiceTest`, verify:

- create swap notifies target account with `SWAP_REQUESTED`.
- target accept notifies requester account with `SWAP_TARGET_ACCEPTED`.
- target reject notifies requester account with `SWAP_TARGET_REJECTED`.

Verification shape:

```java
verify(notificationService).notify(eq(targetAccount), eq("SWAP_REQUESTED"), eq("Bạn có yêu cầu đổi ca mới"), contains("đổi ca"));
verify(notificationService).notify(eq(requesterAccount), eq("SWAP_TARGET_ACCEPTED"), eq("Yêu cầu đổi ca đang chờ quản lý duyệt"), contains("chờ quản lý"));
verify(notificationService).notify(eq(requesterAccount), eq("SWAP_TARGET_REJECTED"), eq("Yêu cầu đổi ca đã bị từ chối"), contains("từ chối"));
```

- [ ] **Step 2: Add failing admin swap log/notification tests**

In `AdminSwapRequestServiceTest`, verify approve/reject records `APPROVE`/`REJECT` logs on `swap_requests` and notifies requester + target accounts with `SWAP_APPROVED` or `SWAP_REJECTED`.

- [ ] **Step 3: Run swap tests and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=EmployeeSwapRequestServiceTest,AdminSwapRequestServiceTest test`

Expected: fails because notification/log services are not called.

- [ ] **Step 4: Implement employee swap notifications**

Inject `NotificationService` into `EmployeeSwapRequestService`. After saving a new swap request, notify `target.getAccount()`. After target response save, notify `swapRequest.getRequesterAssignment().getEmployee().getAccount()` with accepted/rejected type/title.

- [ ] **Step 5: Implement admin swap logs and notifications**

Inject `NotificationService` and `ActivityLogService` into `AdminSwapRequestService`. After approve/reject, notify requester and target accounts and record compact log:

```java
activityLogService.record(manager, action, "swap_requests", swapRequest.getId(), null, Map.of(
        "id", swapRequest.getId(),
        "status", swapRequest.getStatus(),
        "requesterAssignmentId", swapRequest.getRequesterAssignment().getId(),
        "targetEmployeeId", swapRequest.getTargetEmployee().getId()));
```

- [ ] **Step 6: Run swap tests**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=EmployeeSwapRequestServiceTest,AdminSwapRequestServiceTest test`

Expected: pass.

- [ ] **Step 7: Commit swap events**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/employee/EmployeeSwapRequestService.java backend/src/main/java/scheduling/scheduling/admin/AdminSwapRequestService.java backend/src/test/java/scheduling/scheduling/employee/EmployeeSwapRequestServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminSwapRequestServiceTest.java
```

---

### Task 7: Wire Remaining Activity Logs

**Files:**

- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`
- Modify: related service tests.

- [ ] **Step 1: Add failing employee log tests**

In `AdminEmployeeServiceTest`, verify create/update/lock/unlock call `ActivityLogService.record(...)` with `employees` entity type and actions `CREATE`, `UPDATE`, `LOCK`, `UNLOCK`.

- [ ] **Step 2: Add failing assignment log tests**

In `AdminShiftAssignmentServiceTest`, verify create/update/delete/lock/unlock call `ActivityLogService.record(...)` with `shift_assignments` and the matching action.

- [ ] **Step 3: Add failing optimization log tests**

In `AdminOptimizationServiceTest`, verify optimize terminal outcomes call `ActivityLogService.record(...)` with `optimization_runs`, action `OPTIMIZE`, and snapshot including `runId`, `periodId`, and final `status`.

- [ ] **Step 4: Run focused tests and verify RED**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminEmployeeServiceTest,AdminShiftAssignmentServiceTest,AdminOptimizationServiceTest test`

Expected: fails because logs are not called.

- [ ] **Step 5: Inject and call `ActivityLogService` in employee service**

Add the dependency and record compact snapshots after successful saves. Snapshot shape:

```java
Map.of(
        "id", employee.getId(),
        "employeeCode", employee.getEmployeeCode(),
        "fullName", employee.getFullName(),
        "status", employee.getStatus())
```

- [ ] **Step 6: Inject and call `ActivityLogService` in assignment service**

Record assignment snapshots with:

```java
Map.of(
        "id", assignment.getId(),
        "workShiftId", assignment.getWorkShift().getId(),
        "employeeId", assignment.getEmployee().getId(),
        "assignmentStatus", assignment.getAssignmentStatus(),
        "assignedSource", assignment.getAssignedSource(),
        "locked", assignment.getLocked())
```

For delete, use the before snapshot as `oldValue` and `null` as `newValue`.

- [ ] **Step 7: Inject and call `ActivityLogService` in optimization service**

Record after run status is known. Snapshot shape:

```java
Map.of(
        "runId", run.getId(),
        "periodId", period.getId(),
        "status", run.getStatus(),
        "totalPenalty", run.getTotalPenalty())
```

If `totalPenalty` can be null, build a mutable `LinkedHashMap` and omit the key when null.

- [ ] **Step 8: Run focused tests**

Run: `./backend/mvnw.cmd -f backend/pom.xml -Dtest=AdminEmployeeServiceTest,AdminShiftAssignmentServiceTest,AdminOptimizationServiceTest test`

Expected: pass.

- [ ] **Step 9: Commit remaining logs**

Run:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminEmployeeService.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java backend/src/test/java/scheduling/scheduling/admin/AdminEmployeeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java
```

---

### Task 8: Docs And Final Verification

**Files:**

- Modify: `docs/project_plan.md`

- [ ] **Step 1: Mark Phase 7 backend-completed items in project plan**

Mark these Phase 7 checklist items complete if and only if prior tasks are implemented and tested:

```markdown
- [x] Tao service ghi `ActivityLog`
- [x] Ghi log khi tao/sua/xoa nhan vien
- [x] Ghi log khi duyet nghi
- [x] Ghi log khi tao lich tu dong
- [x] Ghi log khi dieu chinh lich
- [x] Ghi log khi cong bo lich
- [x] Ghi log khi xu ly doi ca
- [x] Tao notification khi lich duoc cong bo
- [x] Tao notification khi don nghi duoc duyet/tu choi
- [x] Tao notification khi co yeu cau doi ca
- [x] Tao notification khi yeu cau doi ca duoc duyet/tu choi
- [x] API xem danh sach notification
- [x] API danh dau notification da doc
- [x] API xem activity logs cho quan ly
- [x] Test notification service
- [x] Test activity log service
```

- [ ] **Step 2: Run full backend test suite**

Run: `./backend/mvnw.cmd -f backend/pom.xml test`

Expected: all tests pass, no failures, no errors.

- [ ] **Step 3: Run AI tests as regression check**

Run from `ai-service/`: `./.venv/Scripts/pytest.exe -q`

Expected: all tests pass.

- [ ] **Step 4: Run diff whitespace check**

Run: `git diff --check`

Expected: no output.

- [ ] **Step 5: Inspect status and diff**

Run:

```powershell
git status --short --branch
```

Expected: only intended Phase 7 files modified; unrelated untracked docs remain untracked and are not staged.

- [ ] **Step 6: Commit docs update**

Run:

```powershell
git add docs/project_plan.md
```

---

## Final Review Checklist

- [ ] Notification ownership is enforced with recipient id in repository queries.
- [ ] Activity log API is manager/admin-only.
- [ ] Notification/log writes happen only after successful business validation.
- [ ] Notification/log snapshots are compact maps, not full entities.
- [ ] No frontend mock service was changed in Phase 7.
- [ ] No schema change was added.
- [ ] `./backend/mvnw.cmd -f backend/pom.xml test` passes.
- [ ] `./.venv/Scripts/pytest.exe -q` passes from `ai-service/`.
- [ ] `git diff --check` is clean.

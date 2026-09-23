package scheduling.scheduling.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    void recordsActivityLogSnapshotWithLocalDate() {
        Account manager = account(1L, AccountRole.MANAGER);

        service.record(manager, "APPROVE", "leave_requests", 10L, null, Map.of(
                "startDate", LocalDate.of(2026, 9, 22),
                "endDate", LocalDate.of(2026, 9, 23)));

        verify(activityLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getNewValueJson().contains("2026-09-22")
                        && log.getNewValueJson().contains("2026-09-23")));
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
        when(activityLogRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(java.util.List.of(log));

        var result = service.list(principal(), "PUBLISH", "schedule_periods", 1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo("PUBLISH");
    }

    @Test
    void managerListsActivityLogsWithoutDateFilters() {
        Account manager = account(1L, AccountRole.MANAGER);
        ActivityLog log = ActivityLog.builder()
                .account(manager)
                .action("UPDATE")
                .entityType("employees")
                .entityId(2L)
                .build();
        log.setId(100L);
        log.setCreatedAt(LocalDateTime.of(2026, 9, 21, 8, 30));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(activityLogRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(java.util.List.of(log));

        var result = service.list(principal(), null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo("UPDATE");
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

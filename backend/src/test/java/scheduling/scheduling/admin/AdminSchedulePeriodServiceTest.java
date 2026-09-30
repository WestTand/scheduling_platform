package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.notification.NotificationService;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.OptimizationRunRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class AdminSchedulePeriodServiceTest {

    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final OptimizationRunRepository optimizationRunRepository = mock(OptimizationRunRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final AdminSchedulePeriodService service = new AdminSchedulePeriodService(
            schedulePeriodRepository,
            accountRepository,
            workShiftRepository,
            shiftAssignmentRepository,
            optimizationRunRepository,
            notificationService,
            activityLogService,
            employeeRepository);

    @Test
    void createSchedulePeriodCreatesDraftWithDefaultVersionAndCreator() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)).thenReturn(false);
        when(schedulePeriodRepository.saveAndFlush(any(SchedulePeriod.class))).thenAnswer(invocation -> {
            SchedulePeriod period = invocation.getArgument(0);
            period.setId(20L);
            return period;
        });

        AdminSchedulePeriodResponse response = service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                " Week 38 ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), null));

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.periodName()).isEqualTo("Week 38");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdByAccountId()).isEqualTo(1L);
        assertThat(response.createdByUsername()).isEqualTo("manager01");
    }

    @Test
    void createSchedulePeriodRejectsBlankNameAfterTrim() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "   ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSchedulePeriodRejectsInvalidDateRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 14), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSchedulePeriodRejectsNullStartDate() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", null, LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).existsByStartDateAndEndDateAndVersionNo(any(), any(), any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSchedulePeriodRejectsNullEndDate() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), null, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).existsByStartDateAndEndDateAndVersionNo(any(), any(), any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSchedulePeriodRejectsDuplicateDateRangeAndVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchedulePeriodTranslatesDuplicatePersistenceConflict() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)).thenReturn(false);
        when(schedulePeriodRepository.saveAndFlush(any(SchedulePeriod.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate schedule period"));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchedulePeriodRejectsInvalidVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 0)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchedulePeriodRejectsOverlongNameAfterTrim() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "a".repeat(101), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsNormalizesStatusAndDateFilters() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.searchForAdmin("DRAFT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(period));

        List<AdminSchedulePeriodResponse> response = service.listSchedulePeriods(
                principal, " draft ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(AdminSchedulePeriodResponse::id).containsExactly(20L);
        verify(schedulePeriodRepository).searchForAdmin("DRAFT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void listSchedulePeriodsDefaultsBlankStatusToDraft() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        service.listSchedulePeriods(principal, "   ", null, null);

        verify(schedulePeriodRepository).searchForAdmin("DRAFT", null, null);
    }

    @Test
    void listSchedulePeriodsDefaultsMissingStatusToDraft() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        service.listSchedulePeriods(principal, null, null, null);

        verify(schedulePeriodRepository).searchForAdmin("DRAFT", null, null);
    }

    @Test
    void listSchedulePeriodsRejectsAllStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(
                principal, " all ", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 26)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsRejectsUnsupportedStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(principal, "ARCHIVED", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsAcceptsLifecycleStatuses() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        service.listSchedulePeriods(principal, " approved ", null, null);
        service.listSchedulePeriods(principal, "PUBLISHED", null, null);
        service.listSchedulePeriods(principal, "CANCELLED", null, null);

        verify(schedulePeriodRepository).searchForAdmin("APPROVED", null, null);
        verify(schedulePeriodRepository).searchForAdmin("PUBLISHED", null, null);
        verify(schedulePeriodRepository).searchForAdmin("CANCELLED", null, null);
    }

    @Test
    void listSchedulePeriodsRejectsInvertedDateFilter() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(
                principal, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.listSchedulePeriods(principal, null, null, null));

        verify(schedulePeriodRepository, never()).searchForAdmin(any(), any(), any());
    }

    @Test
    void listSchedulePeriodsRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.listSchedulePeriods(principal, null, null, null));

        verify(schedulePeriodRepository, never()).searchForAdmin(any(), any(), any());
    }

    @Test
    void getSchedulePeriodReturnsMappedDetail() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        Account approver = account(new CurrentAccountPrincipal(3L, "approver01", AccountRole.MANAGER));
        period.setApprovedBy(approver);
        period.setPublishedAt(LocalDateTime.of(2026, 9, 21, 9, 0));
        period.setTotalPenalty(new BigDecimal("12.50"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.getSchedulePeriod(principal, 20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.createdByUsername()).isEqualTo("manager01");
        assertThat(response.approvedByAccountId()).isEqualTo(3L);
        assertThat(response.approvedByUsername()).isEqualTo("approver01");
        assertThat(response.createdAt()).isNull();
        assertThat(response.publishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 0));
        assertThat(response.totalPenalty()).isEqualByComparingTo("12.50");
    }

    @Test
    void createSchedulePeriodRejectsInvalidDateRangeWithGenericInvalidRequestMessage() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 14), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid schedule period request");
    }

    @Test
    void getSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.getSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void getSchedulePeriodRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.getSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void updateSchedulePeriodUpdatesDraftFieldsWithoutAlteringMetadata() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account creator = account(principal);
        Account approver = account(new CurrentAccountPrincipal(3L, "approver01", AccountRole.MANAGER));
        SchedulePeriod period = draftPeriod(20L, creator);
        period.setApprovedBy(approver);
        period.setPublishedAt(LocalDateTime.of(2026, 9, 21, 9, 0));
        period.setTotalPenalty(new BigDecimal("12.50"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2, 20L)).thenReturn(false);
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                " Week 40 ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2));

        assertThat(response.periodName()).isEqualTo("Week 40");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.versionNo()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdByAccountId()).isEqualTo(1L);
        assertThat(response.approvedByAccountId()).isEqualTo(3L);
        assertThat(response.publishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 0));
        assertThat(response.totalPenalty()).isEqualByComparingTo("12.50");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void updateSchedulePeriodDefaultsNullVersionToOne() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 1, 20L)).thenReturn(false);
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), null));

        assertThat(response.versionNo()).isEqualTo(1);
    }

    @Test
    void updateSchedulePeriodRejectsInvalidDateRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 1), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodRejectsNullStartDate() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", null, LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).existsByStartDateAndEndDateAndVersionNoAndIdNot(any(), any(), any(), any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodRejectsNullEndDate() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), null, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).existsByStartDateAndEndDateAndVersionNoAndIdNot(any(), any(), any(), any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateSchedulePeriodRejectsNonDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodRejectsDuplicateDateRangeAndVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodTranslatesDuplicatePersistenceConflict() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2, 20L)).thenReturn(false);
        when(schedulePeriodRepository.saveAndFlush(period))
                .thenThrow(new DataIntegrityViolationException("duplicate schedule period"));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)));

        verify(schedulePeriodRepository, never()).findDetailById(any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSchedulePeriodRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)));

        verify(schedulePeriodRepository, never()).findDetailById(any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createSchedulePeriodRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveSchedulePeriodApprovesFullyStaffedDraft() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        WorkShift shift = staffedShift(200L, 2);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first), shiftAssignment(301L, shift, second)));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.approveSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedByAccountId()).isEqualTo(1L);
        assertThat(response.approvedByUsername()).isEqualTo("manager01");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void approveSchedulePeriodRejectsInfeasibleOptimizationRun() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        WorkShift shift = staffedShift(200L, 2);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first), shiftAssignment(301L, shift, second)));
        when(optimizationRunRepository.findTopBySchedulePeriodIdOrderByStartedAtDescIdDesc(20L))
                .thenReturn(Optional.of(OptimizationRun.builder()
                        .schedulePeriod(period)
                        .status("INFEASIBLE")
                        .startedAt(LocalDateTime.of(2026, 9, 20, 8, 0))
                        .build()));

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Lịch đang có ràng buộc chưa thỏa nên không thể duyệt.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(schedulePeriodRepository, never()).saveAndFlush(any(SchedulePeriod.class));
    }

    @Test
    void approveSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.approveSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void approveSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void approveSchedulePeriodRejectsPublishedAndCancelledPeriods() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        SchedulePeriod published = draftPeriod(20L, account(principal));
        published.setStatus("PUBLISHED");
        SchedulePeriod cancelled = draftPeriod(21L, account(principal));
        cancelled.setStatus("CANCELLED");
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(published));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveSchedulePeriodRejectsEmptyPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of());

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveSchedulePeriodRejectsUnderstaffedShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        WorkShift shift = staffedShift(200L, 2);
        Employee only = activeEmployee(10L, "EMP010", "Employee Ten");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, only)));

        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void reapproveSchedulePeriodValidatesStaffingAgain() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        Account originalApprover = account(new CurrentAccountPrincipal(3L, "approver01", AccountRole.MANAGER));
        SchedulePeriod passing = draftPeriod(20L, manager);
        passing.setStatus("APPROVED");
        passing.setApprovedBy(originalApprover);
        SchedulePeriod failing = draftPeriod(21L, manager);
        failing.setStatus("APPROVED");
        failing.setApprovedBy(originalApprover);
        WorkShift shift = staffedShift(200L, 2);
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(passing));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(failing));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(shift));
        when(workShiftRepository.searchForPeriod(21L, null, null)).thenReturn(List.of(shift));
        when(shiftAssignmentRepository.listForPeriod(20L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first), shiftAssignment(301L, shift, second)));
        when(shiftAssignmentRepository.listForPeriod(21L))
                .thenReturn(List.of(shiftAssignment(300L, shift, first)));

        AdminSchedulePeriodResponse response = service.approveSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedByAccountId()).isEqualTo(3L);
        assertThat(response.approvedByUsername()).isEqualTo("approver01");
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
        assertThatThrownBy(() -> service.approveSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publishSchedulePeriodPublishesApprovedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("APPROVED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.publishSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void publishSchedulePeriodNotifiesAssigneesAndRecordsLog() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        period.setStatus("APPROVED");
        WorkShift shift = staffedShift(200L, 2);
        Account firstAccount = account(new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE));
        Account secondAccount = account(new CurrentAccountPrincipal(3L, "employee02", AccountRole.EMPLOYEE));
        Employee first = activeEmployee(10L, "EMP010", "Employee Ten");
        first.setAccount(firstAccount);
        Employee second = activeEmployee(11L, "EMP011", "Employee Eleven");
        second.setAccount(secondAccount);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(shiftAssignmentRepository.listForPeriod(20L)).thenReturn(List.of(
                shiftAssignment(300L, shift, first),
                shiftAssignment(301L, shift, first),
                shiftAssignment(302L, shift, second)));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.publishSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(activityLogService).record(
                same(manager), eq("PUBLISH"), eq("schedule_periods"), eq(period.getId()), any(), any());
        verify(notificationService).notifyAll(
                argThat(recipients -> recipients != null && recipients.size() == 2),
                eq("SCHEDULE_PUBLISHED"),
                eq("Lịch làm việc mới đã được công bố"),
                contains(period.getPeriodName()));
    }

    @Test
    void republishSchedulePeriodDoesNotNotifyOrLog() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        SchedulePeriod period = draftPeriod(20L, manager);
        period.setStatus("PUBLISHED");
        period.setPublishedAt(LocalDateTime.of(2026, 9, 21, 9, 0));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        service.publishSchedulePeriod(principal, 20L);

        verify(notificationService, never()).notifyAll(any(), any(), any(), any());
        verify(activityLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishSchedulePeriodRejectsNonApprovedPeriods() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        SchedulePeriod draft = draftPeriod(20L, account(principal));
        SchedulePeriod cancelled = draftPeriod(21L, account(principal));
        cancelled.setStatus("CANCELLED");
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draft));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 21L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void republishSchedulePeriodKeepsOriginalPublishedAt() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 21, 9, 0);
        period.setPublishedAt(publishedAt);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.publishSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.publishSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void cancelSchedulePeriodCancelsDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void cancelSchedulePeriodCancelsApprovedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("APPROVED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void cancelSchedulePeriodRejectsPublishedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.cancelSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void recancelSchedulePeriodIsIdempotent() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("CANCELLED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.cancelSchedulePeriod(principal, 20L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.cancelSchedulePeriod(principal, 20L));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void updateEmployeePoolReplacesTheDraftPool() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setEmployeePool(new LinkedHashSet<>(List.of(schedulableEmployee(10L, "EMP010", "Employee Ten"))));
        Employee first = schedulableEmployee(10L, "EMP010", "Employee Ten");
        Employee second = schedulableEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.updateEmployeePool(principal, 20L, List.of(10L, 11L));

        assertThat(period.getEmployeePool()).extracting(Employee::getId).containsExactly(10L, 11L);
        assertThat(response.id()).isEqualTo(20L);
        verify(schedulePeriodRepository).saveAndFlush(period);
    }

    @Test
    void updateEmployeePoolIgnoresDuplicateEmployeeIds() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(schedulableEmployee(10L, "EMP010", "Employee Ten")));
        when(schedulePeriodRepository.saveAndFlush(period)).thenReturn(period);

        service.updateEmployeePool(principal, 20L, List.of(10L, 10L));

        assertThat(period.getEmployeePool()).extracting(Employee::getId).containsExactly(10L);
    }

    @Test
    void updateEmployeePoolRejectsPublishedAndCancelledPeriods() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee existing = schedulableEmployee(10L, "EMP010", "Employee Ten");
        SchedulePeriod published = draftPeriod(20L, account(principal));
        published.setStatus("PUBLISHED");
        published.setEmployeePool(new LinkedHashSet<>(List.of(existing)));
        SchedulePeriod cancelled = draftPeriod(21L, account(principal));
        cancelled.setStatus("CANCELLED");
        cancelled.setEmployeePool(new LinkedHashSet<>(List.of(existing)));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(published));
        when(schedulePeriodRepository.findDetailById(21L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid schedule period request")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.updateEmployeePool(principal, 21L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(published.getEmployeePool()).extracting(Employee::getId).containsExactly(10L);
        assertThat(cancelled.getEmployeePool()).extracting(Employee::getId).containsExactly(10L);
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsUnknownEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setEmployeePool(new LinkedHashSet<>(List.of(schedulableEmployee(10L, "EMP010", "Employee Ten"))));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(schedulableEmployee(10L, "EMP010", "Employee Ten")));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 99L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Danh sách nhân viên không hợp lệ.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(period.getEmployeePool()).extracting(Employee::getId).containsExactly(10L);
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsInactiveEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        Employee inactive = activeEmployee(11L, "EMP011", "Employee Eleven");
        inactive.setStatus("INACTIVE");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(schedulableEmployee(10L, "EMP010", "Employee Ten"), inactive));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Danh sách nhân viên không hợp lệ.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsEmployeeWithoutActiveEmployeeAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        Employee valid = schedulableEmployee(10L, "EMP010", "Employee Ten");
        Employee missingAccount = activeEmployee(11L, "EMP011", "Employee Eleven");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(valid, missingAccount));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Danh sách nhân viên không hợp lệ.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsEmptyEmployeeIdList() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Danh sách nhân viên không được để trống.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(employeeRepository, never()).findAllById(any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsNullEmployeeIdList() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Danh sách nhân viên không được để trống.")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(employeeRepository, never()).findAllById(any());
        verify(schedulePeriodRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEmployeePoolRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)));

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    @Test
    void updateEmployeePoolRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEmployeePool(principal, 20L, List.of(10L, 11L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static void assertForbidden(ThrowingServiceCall call) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @FunctionalInterface
    private interface ThrowingServiceCall {
        void execute();
    }

    private static CurrentAccountPrincipal managerPrincipal() {
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

    private static SchedulePeriod draftPeriod(Long id, Account manager) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .createdBy(manager)
                .build();
        period.setId(id);
        return period;
    }

    private static WorkShift staffedShift(Long id, int requiredStaff) {
        WorkShift shift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(requiredStaff)
                .build();
        shift.setId(id);
        return shift;
    }

    private static Employee activeEmployee(Long id, String code, String name) {
        Employee employee = Employee.builder()
                .employeeCode(code)
                .fullName(name)
                .status("ACTIVE")
                .build();
        employee.setId(id);
        return employee;
    }

    private static Employee schedulableEmployee(Long id, String code, String name) {
        Employee employee = activeEmployee(id, code, name);
        Account account = Account.builder()
                .username(code.toLowerCase() + "@example.com")
                .passwordHash("hash")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(id + 100L);
        employee.setAccount(account);
        return employee;
    }

    private static ShiftAssignment shiftAssignment(Long id, WorkShift shift, Employee employee) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        assignment.setId(id);
        return assignment;
    }
}

package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.WorkShiftRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

class AdminSchedulePeriodServiceTest {

    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final AdminSchedulePeriodService service = new AdminSchedulePeriodService(
            schedulePeriodRepository, accountRepository, workShiftRepository, shiftAssignmentRepository);

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
    void listSchedulePeriodsRejectsUnsupportedStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(principal, "PUBLISHED", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
        assertThat(response.publishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 0));
        assertThat(response.totalPenalty()).isEqualByComparingTo("12.50");
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
}

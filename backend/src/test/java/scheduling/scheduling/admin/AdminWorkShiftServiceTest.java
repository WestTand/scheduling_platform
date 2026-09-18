package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class AdminWorkShiftServiceTest {

    private final WorkShiftRepository workShiftRepository = org.mockito.Mockito.mock(WorkShiftRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = org.mockito.Mockito.mock(SchedulePeriodRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = org.mockito.Mockito.mock(ShiftTypeRepository.class);
    private final AccountRepository accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
    private final DayClassificationService dayClassificationService =
            org.mockito.Mockito.mock(DayClassificationService.class);
    private final AdminWorkShiftService service = new AdminWorkShiftService(
            workShiftRepository,
            schedulePeriodRepository,
            shiftTypeRepository,
            accountRepository,
            dayClassificationService);

    @BeforeEach
    void stubDefaultDayClassification() {
        when(dayClassificationService.classify(any(LocalDate.class)))
                .thenReturn(DayClassificationService.DayType.WEEKDAY);
    }

    @Test
    void createWorkShiftCreatesShiftInDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        ShiftType shiftType = activeShiftType();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(200L);
            return workShift;
        });

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 2, " Morning coverage "));

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.periodId()).isEqualTo(20L);
        assertThat(response.shiftTypeId()).isEqualTo(100L);
        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.requiredStaff()).isEqualTo(2);
        assertThat(response.note()).isEqualTo("Morning coverage");
    }

    @Test
    void createWorkShiftDefaultsRequiredStaffToShiftTypeMinimum() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        ShiftType shiftType = activeShiftType();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), null, "   "));

        assertThat(response.requiredStaff()).isEqualTo(1);
        assertThat(response.note()).isNull();
    }

    @Test
    void createWorkShiftRejectsNonDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftRejectsInactiveShiftType() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftType shiftType = activeShiftType();
        shiftType.setStatus("INACTIVE");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsDateOutsidePeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 30), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsRequiredStaffOutsideShiftTypeRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 3, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsOverlongNoteAfterTrim() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, "a".repeat(1001))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsMissingPeriodOrShiftType() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createWorkShiftAllowsWeekdayShiftOnWeekday() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(weekdayShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 14)))
                .thenReturn(DayClassificationService.DayType.WEEKDAY);
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(200L);
            return workShift;
        });

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null));

        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void createWorkShiftRejectsWeekdayShiftOnWeekend() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(weekdayShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 19)))
                .thenReturn(DayClassificationService.DayType.WEEKEND);

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 19), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Shift type is not applicable on this date")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftAllowsOnCallShiftOnWeekend() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(onCallShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 19)))
                .thenReturn(DayClassificationService.DayType.WEEKEND);
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(200L);
            return workShift;
        });

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 19), 1, null));

        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 9, 19));
    }

    @Test
    void createWorkShiftAllowsOnCallShiftOnActiveHolidayWeekday() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(onCallShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 14)))
                .thenReturn(DayClassificationService.DayType.HOLIDAY);
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(200L);
            return workShift;
        });

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null));

        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void createWorkShiftRejectsOnCallShiftOnNormalWeekday() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(onCallShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 14)))
                .thenReturn(DayClassificationService.DayType.WEEKDAY);

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Shift type is not applicable on this date")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftRejectsWeekdayShiftOnHolidayWithoutHolidayFlag() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(weekdayShiftType()));
        when(dayClassificationService.classify(LocalDate.of(2026, 9, 14)))
                .thenReturn(DayClassificationService.DayType.HOLIDAY);

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Shift type is not applicable on this date")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void listWorkShiftsValidatesManagerAndDelegatesFilters() {
        CurrentAccountPrincipal principal = managerPrincipal();
        WorkShift workShift = workShift();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(workShiftRepository.searchForPeriod(20L, LocalDate.of(2026, 9, 14), 100L)).thenReturn(List.of(workShift));

        List<AdminWorkShiftResponse> response = service.listWorkShifts(
                principal, 20L, LocalDate.of(2026, 9, 14), 100L);

        assertThat(response).extracting(AdminWorkShiftResponse::id).containsExactly(200L);
        verify(workShiftRepository).searchForPeriod(20L, LocalDate.of(2026, 9, 14), 100L);
    }

    @Test
    void listWorkShiftsRejectsFilterDateOutsidePeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));

        assertThatThrownBy(() -> service.listWorkShifts(principal, 20L, LocalDate.of(2026, 9, 30), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getWorkShiftReturnsDetailOrNotFound() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));

        AdminWorkShiftResponse response = service.getWorkShift(principal, 200L);

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.periodName()).isEqualTo("Week 38");
        assertThat(response.shiftName()).isEqualTo("Morning");

        when(workShiftRepository.findDetailById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getWorkShift(principal, 404L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createWorkShiftRejectsNullPrincipal() {
        assertForbidden(() -> service.createWorkShift(null, 20L, createRequest()));

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.createWorkShift(principal, 20L, createRequest()));

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.createWorkShift(principal, 20L, createRequest()));

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void listWorkShiftsRejectsNullPrincipal() {
        assertForbidden(() -> service.listWorkShifts(null, 20L, LocalDate.of(2026, 9, 14), 100L));

        verify(workShiftRepository, never()).searchForPeriod(any(), any(), any());
    }

    @Test
    void listWorkShiftsRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.listWorkShifts(principal, 20L, LocalDate.of(2026, 9, 14), 100L));

        verify(workShiftRepository, never()).searchForPeriod(any(), any(), any());
    }

    @Test
    void listWorkShiftsRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.listWorkShifts(principal, 20L, LocalDate.of(2026, 9, 14), 100L));

        verify(workShiftRepository, never()).searchForPeriod(any(), any(), any());
    }

    @Test
    void getWorkShiftRejectsNullPrincipal() {
        assertForbidden(() -> service.getWorkShift(null, 200L));

        verify(workShiftRepository, never()).findDetailById(any());
    }

    @Test
    void getWorkShiftRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertForbidden(() -> service.getWorkShift(principal, 200L));

        verify(workShiftRepository, never()).findDetailById(any());
    }

    @Test
    void getWorkShiftRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertForbidden(() -> service.getWorkShift(principal, 200L));

        verify(workShiftRepository, never()).findDetailById(any());
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

    private static CreateWorkShiftRequest createRequest() {
        return new CreateWorkShiftRequest(100L, LocalDate.of(2026, 9, 14), 1, null);
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

    private static SchedulePeriod draftPeriod() {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .createdBy(account(managerPrincipal()))
                .build();
        period.setId(20L);
        return period;
    }

    private static ShiftType activeShiftType() {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Morning")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .applicableWeekdays("MON,TUE,WED,THU,FRI,SAT,SUN")
                .appliesOnHolidays(false)
                .penaltyWeightMultiplier(new BigDecimal("1.00"))
                .build();
        shiftType.setId(100L);
        return shiftType;
    }

    private static ShiftType weekdayShiftType() {
        ShiftType shiftType = activeShiftType();
        shiftType.setApplicableWeekdays("MON,TUE,WED,THU,FRI");
        shiftType.setAppliesOnHolidays(false);
        return shiftType;
    }

    private static ShiftType onCallShiftType() {
        ShiftType shiftType = activeShiftType();
        shiftType.setShiftName("Truc sang");
        shiftType.setApplicableWeekdays("SAT,SUN");
        shiftType.setAppliesOnHolidays(true);
        return shiftType;
    }

    private static WorkShift workShift() {
        WorkShift workShift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(activeShiftType())
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .note("Morning coverage")
                .build();
        workShift.setId(200L);
        return workShift;
    }
}

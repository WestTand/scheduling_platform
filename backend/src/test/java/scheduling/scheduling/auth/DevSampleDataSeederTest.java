package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.SwapRequestRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class DevSampleDataSeederTest {

    private final AccountRepository accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
    private final EmployeeRepository employeeRepository = org.mockito.Mockito.mock(EmployeeRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = org.mockito.Mockito.mock(ShiftTypeRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = org.mockito.Mockito.mock(SchedulePeriodRepository.class);
    private final WorkShiftRepository workShiftRepository = org.mockito.Mockito.mock(WorkShiftRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = org.mockito.Mockito.mock(ShiftAssignmentRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = org.mockito.Mockito.mock(LeaveRequestRepository.class);
    private final PreferenceRepository preferenceRepository = org.mockito.Mockito.mock(PreferenceRepository.class);
    private final SwapRequestRepository swapRequestRepository = org.mockito.Mockito.mock(SwapRequestRepository.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);

    @BeforeEach
    void setUp() {
        when(employeeRepository.findByEmployeeCode(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Employee@123")).thenReturn("encoded-employee-password");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsActiveEmployeeAccountsFromSeedDefinitions() throws Exception {
        newSeeder().run(null);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, org.mockito.Mockito.atLeast(4)).save(accountCaptor.capture());

        assertThat(accountCaptor.getAllValues())
                .filteredOn(account -> "emp001@example.com".equals(account.getUsername()))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.getPasswordHash()).isEqualTo("encoded-employee-password");
                    assertThat(account.getRole()).isEqualTo(AccountRole.EMPLOYEE);
                    assertThat(account.getIsActive()).isTrue();
                    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
                    assertThat(account.getEmployee()).isNotNull();
                    assertThat(account.getEmployee().getEmployeeCode()).isEqualTo("EMP001");
                });
    }

    @Test
    void skipsScheduleSamplesWhenCanonicalShiftTypesAreMissing() throws Exception {
        newSeeder().run(null);

        verify(schedulePeriodRepository, never()).save(any());
        verify(workShiftRepository, never()).save(any());
        verify(shiftAssignmentRepository, never()).save(any());
        verify(leaveRequestRepository, never()).save(any());
        verify(preferenceRepository, never()).save(any());
        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void seedsWorkShiftsWithResolvedTimeWindowsForAiOptimization() throws Exception {
        Account manager = Account.builder().username("manager@example.com").role(AccountRole.MANAGER).build();
        manager.setId(1L);
        when(accountRepository.findByUsername("manager@example.com")).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findByStartDateAndEndDateAndVersionNo(any(), any(), eq(1))).thenReturn(Optional.empty());
        when(schedulePeriodRepository.save(any(SchedulePeriod.class))).thenAnswer(invocation -> {
            SchedulePeriod period = invocation.getArgument(0);
            period.setId(1L);
            return period;
        });
        when(workShiftRepository.findBySchedulePeriodIdAndWorkDateAndShiftTypeId(any(), any(), any())).thenReturn(Optional.empty());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(10L);
            return workShift;
        });
        when(shiftAssignmentRepository.findByWorkShiftIdAndEmployeeId(any(), any())).thenReturn(Optional.empty());
        when(shiftAssignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTypeRepository.findByShiftCode("THONG_TAM")).thenReturn(Optional.of(shiftType(1L, "THONG_TAM", "Thong tam", LocalTime.of(8, 0), LocalTime.of(17, 0), false, "MON,TUE,WED,THU,FRI")));
        when(shiftTypeRepository.findByShiftCode("NGOAI_GIO_1")).thenReturn(Optional.of(shiftType(2L, "NGOAI_GIO_1", "Ngoai gio 1", LocalTime.of(17, 0), LocalTime.of(0, 30), true, "MON,TUE,WED,THU,FRI")));
        when(shiftTypeRepository.findByShiftCode("TRUC_SANG")).thenReturn(Optional.of(shiftType(3L, "TRUC_SANG", "Truc sang", LocalTime.of(8, 0), LocalTime.of(16, 0), false, "SAT,SUN")));

        newSeeder().run(null);

        ArgumentCaptor<WorkShift> workShiftCaptor = ArgumentCaptor.forClass(WorkShift.class);
        verify(workShiftRepository, org.mockito.Mockito.atLeast(3)).save(workShiftCaptor.capture());
        assertThat(workShiftCaptor.getAllValues()).allSatisfy(workShift -> {
            assertThat(workShift.getStartsAt()).isNotNull();
            assertThat(workShift.getEndsAt()).isNotNull();
            assertThat(workShift.getWorkMinutes()).isPositive();
            assertThat(workShift.getTransitionShift()).isNotNull();
        });
    }

    @Test
    void backfillsExistingSeededWorkShiftTimeWindows() throws Exception {
        Account manager = Account.builder().username("manager@example.com").role(AccountRole.MANAGER).build();
        manager.setId(1L);
        SchedulePeriod period = SchedulePeriod.builder().build();
        period.setId(1L);
        ShiftType thongTam = shiftType(1L, "THONG_TAM", "Thong tam", LocalTime.of(8, 0), LocalTime.of(17, 0), false, "MON,TUE,WED,THU,FRI");
        ShiftType ngoaiGio1 = shiftType(2L, "NGOAI_GIO_1", "Ngoai gio 1", LocalTime.of(17, 0), LocalTime.of(0, 30), true, "MON,TUE,WED,THU,FRI");
        ShiftType trucSang = shiftType(3L, "TRUC_SANG", "Truc sang", LocalTime.of(8, 0), LocalTime.of(16, 0), false, "SAT,SUN");
        WorkShift existing = WorkShift.builder().schedulePeriod(period).shiftType(thongTam).workDate(java.time.LocalDate.of(2026, 9, 21)).requiredStaff(2).build();
        existing.setId(10L);
        when(accountRepository.findByUsername("manager@example.com")).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findByStartDateAndEndDateAndVersionNo(any(), any(), eq(1))).thenReturn(Optional.of(period));
        when(workShiftRepository.findBySchedulePeriodIdAndWorkDateAndShiftTypeId(eq(1L), eq(java.time.LocalDate.of(2026, 9, 21)), eq(1L))).thenReturn(Optional.of(existing));
        when(workShiftRepository.findBySchedulePeriodIdAndWorkDateAndShiftTypeId(eq(1L), eq(java.time.LocalDate.of(2026, 9, 22)), eq(2L))).thenReturn(Optional.empty());
        when(workShiftRepository.findBySchedulePeriodIdAndWorkDateAndShiftTypeId(eq(1L), eq(java.time.LocalDate.of(2026, 9, 26)), eq(3L))).thenReturn(Optional.empty());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftAssignmentRepository.findByWorkShiftIdAndEmployeeId(any(), any())).thenReturn(Optional.empty());
        when(shiftAssignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTypeRepository.findByShiftCode("THONG_TAM")).thenReturn(Optional.of(thongTam));
        when(shiftTypeRepository.findByShiftCode("NGOAI_GIO_1")).thenReturn(Optional.of(ngoaiGio1));
        when(shiftTypeRepository.findByShiftCode("TRUC_SANG")).thenReturn(Optional.of(trucSang));

        newSeeder().run(null);

        assertThat(existing.getStartsAt()).isNotNull();
        assertThat(existing.getEndsAt()).isNotNull();
        verify(workShiftRepository).save(existing);
    }

    private DevSampleDataSeeder newSeeder() {
        return new DevSampleDataSeeder(
                accountRepository,
                employeeRepository,
                shiftTypeRepository,
                schedulePeriodRepository,
                workShiftRepository,
                shiftAssignmentRepository,
                leaveRequestRepository,
                preferenceRepository,
                swapRequestRepository,
                passwordEncoder,
                "manager@example.com",
                "Employee@123");
    }

    private static ShiftType shiftType(Long id, String code, String name, LocalTime start, LocalTime end, boolean overnight, String weekdays) {
        ShiftType shiftType = ShiftType.builder()
                .shiftCode(code)
                .shiftName(name)
                .startTime(start)
                .endTime(end)
                .durationHours(BigDecimal.valueOf(overnight ? 7.5 : 8.0))
                .overnight(overnight)
                .minStaff(1)
                .maxStaff(20)
                .applicableWeekdays(weekdays)
                .status("ACTIVE")
                .build();
        shiftType.setId(id);
        return shiftType;
    }
}

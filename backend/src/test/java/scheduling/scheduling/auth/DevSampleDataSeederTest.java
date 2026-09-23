package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
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
}

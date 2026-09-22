package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;

class EmployeePreferenceServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeePreferenceService service = new EmployeePreferenceService(
            employeeAccess, preferenceRepository, shiftTypeRepository);

    @Test
    void listPreferencesFiltersByCurrentEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.searchForEmployee(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(preference));

        List<EmployeePreferenceResponse> response = service.listPreferences(
                principal, " PENDING ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(EmployeePreferenceResponse::id).containsExactly(20L);
        verify(preferenceRepository).searchForEmployee(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void listPreferencesRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.listPreferences(principal, "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listPreferencesRejectsInvertedDateFilter() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.listPreferences(
                principal, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "WANT", "AVOID" })
    void createPreferenceAcceptsValidTypes(String preferenceType) {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        ShiftType shiftType = shiftType(10L, "ACTIVE");
        CreatePreferenceRequest request = createRequest(preferenceType, 10L);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), 10L, preferenceType, null)).thenReturn(false);
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> {
            Preference saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        EmployeePreferenceResponse response = service.createPreference(principal, request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.preferenceType()).isEqualTo(preferenceType);
        assertThat(response.shiftTypeId()).isEqualTo(10L);
        assertThat(response.shiftName()).isEqualTo("Morning");
        assertThat(response.shiftCode()).isEqualTo("THONG_TAM");
        assertThat(response.employeeName()).isEqualTo("Employee One");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(accountRepository).findByIdForUpdate(2L);
    }

    @Test
    void createPreferenceSupportsNullShiftTypeAndBlankNote() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreatePreferenceRequest request = new CreatePreferenceRequest(LocalDate.of(2026, 9, 14), "WANT", null, 1, "   ");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "WANT", null)).thenReturn(false);
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.createPreference(principal, request);

        assertThat(response.shiftTypeId()).isNull();
        assertThat(response.shiftCode()).isNull();
        assertThat(response.note()).isNull();
    }

    @Test
    void createPreferenceRejectsInvalidType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.createPreference(principal, createRequest("MAYBE", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreferenceRejectsMissingShiftType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPreference(principal, createRequest("WANT", 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreferenceRejectsInactiveShiftType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType(10L, "INACTIVE")));

        assertThatThrownBy(() -> service.createPreference(principal, createRequest("WANT", 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreferenceRejectsDuplicateActivePreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreatePreferenceRequest request = createRequest("WANT", null);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "WANT", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createPreference(principal, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void updatePreferenceOnlyUpdatesOwnPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        UpdatePreferenceRequest request = new UpdatePreferenceRequest(LocalDate.of(2026, 9, 16), "AVOID", null, 2, "Avoid that day");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeIdForUpdate(20L, 7L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "AVOID", 20L)).thenReturn(false);
        when(preferenceRepository.save(preference)).thenReturn(preference);

        EmployeePreferenceResponse response = service.updatePreference(principal, 20L, request);

        assertThat(response.preferenceDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(response.preferenceType()).isEqualTo("AVOID");
        assertThat(response.priority()).isEqualTo(2);
        verify(preferenceRepository).findByIdAndEmployeeIdForUpdate(20L, 7L);
    }

    @Test
    void updatePreferenceRejectsDuplicateExcludingCurrentPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        UpdatePreferenceRequest request = new UpdatePreferenceRequest(LocalDate.of(2026, 9, 16), "AVOID", null, 2, "Avoid that day");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeIdForUpdate(20L, 7L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "AVOID", 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.updatePreference(principal, 20L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void updatePreferenceRejectsNonPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "APPROVED");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeIdForUpdate(20L, 7L)).thenReturn(Optional.of(preference));

        assertThatThrownBy(() -> service.updatePreference(principal, 20L, updateRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelPreferenceOnlyCancelsOwnPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeIdForUpdate(20L, 7L)).thenReturn(Optional.of(preference));

        service.cancelPreference(principal, 20L);

        assertThat(preference.getStatus()).isEqualTo("CANCELLED");
        verify(preferenceRepository).findByIdAndEmployeeIdForUpdate(20L, 7L);
        verify(preferenceRepository).save(preference);
    }

    @Test
    void cancelPreferenceRejectsNonPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "REJECTED");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeIdForUpdate(20L, 7L)).thenReturn(Optional.of(preference));

        assertThatThrownBy(() -> service.cancelPreference(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static CreatePreferenceRequest createRequest(String preferenceType, Long shiftTypeId) {
        return new CreatePreferenceRequest(LocalDate.of(2026, 9, 14), preferenceType, shiftTypeId, 1, "note");
    }

    private static UpdatePreferenceRequest updateRequest() {
        return new UpdatePreferenceRequest(LocalDate.of(2026, 9, 16), "AVOID", null, 2, "Avoid that day");
    }

    private static CurrentAccountPrincipal employeePrincipal() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static Account account(CurrentAccountPrincipal principal, Employee employee) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .employee(employee)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static Employee employee(Long id, String status) {
        Employee employee = Employee.builder().employeeCode("E001").fullName("Employee One").status(status).build();
        employee.setId(id);
        return employee;
    }

    private static ShiftType shiftType(Long id, String status) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Morning")
                .shiftCode("THONG_TAM")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status(status)
                .build();
        shiftType.setId(id);
        return shiftType;
    }

    private static Preference preference(Long id, Employee employee, ShiftType shiftType, String preferenceType, String status) {
        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(LocalDate.of(2026, 9, 14))
                .preferenceType(preferenceType)
                .priority(1)
                .note("note")
                .status(status)
                .build();
        preference.setId(id);
        return preference;
    }
}

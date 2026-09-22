package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.repository.PreferenceRepository;

class AdminPreferenceServiceTest {

    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final AdminPreferenceService service = new AdminPreferenceService(preferenceRepository);

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Employee employee(Long id) {
        Employee employee = Employee.builder().employeeCode("E001").fullName("Employee One").status("ACTIVE").build();
        employee.setId(id);
        return employee;
    }

    private static Preference preference(Long id, String status) {
        Preference preference = Preference.builder()
                .employee(employee(7L))
                .shiftType(null)
                .preferenceDate(LocalDate.of(2026, 9, 14))
                .preferenceType("WANT")
                .priority(1)
                .note("note")
                .status(status)
                .build();
        preference.setId(id);
        return preference;
    }

    @Test
    void listPreferencesFiltersByStatusAndDateRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Preference preference = preference(50L, "PENDING");
        when(preferenceRepository.searchForAdmin("PENDING", from, to)).thenReturn(List.of(preference));

        List<EmployeePreferenceResponse> response = service.listPreferences(principal, "PENDING", from, to);

        assertThat(response).extracting(EmployeePreferenceResponse::id).containsExactly(50L);
        assertThat(response.get(0).employeeName()).isEqualTo("Employee One");
        assertThat(response.get(0).employeeId()).isEqualTo(7L);
        verify(preferenceRepository).searchForAdmin("PENDING", from, to);
    }

    @Test
    void listPreferencesRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();

        assertThatThrownBy(() -> service.listPreferences(principal, "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approvePreferenceOnlyReviewsPendingPreference() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Preference preference = preference(50L, "PENDING");
        when(preferenceRepository.findByIdForAdminReview(50L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.approvePreference(principal, 50L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.employeeName()).isEqualTo("Employee One");
        assertThat(preference.getStatus()).isEqualTo("APPROVED");
        verify(preferenceRepository).save(preference);
    }

    @Test
    void rejectPreferenceOnlyReviewsPendingPreference() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Preference preference = preference(50L, "PENDING");
        when(preferenceRepository.findByIdForAdminReview(50L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.rejectPreference(principal, 50L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(preference.getStatus()).isEqualTo("REJECTED");
        verify(preferenceRepository).save(preference);
    }

    @Test
    void reviewRejectsNonPendingPreference() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Preference preference = preference(50L, "APPROVED");
        when(preferenceRepository.findByIdForAdminReview(50L)).thenReturn(Optional.of(preference));

        assertThatThrownBy(() -> service.approvePreference(principal, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(preferenceRepository, never()).save(any());
    }
}

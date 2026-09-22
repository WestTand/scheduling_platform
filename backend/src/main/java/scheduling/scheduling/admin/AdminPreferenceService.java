package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.repository.PreferenceRepository;

@Service
public class AdminPreferenceService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final PreferenceRepository preferenceRepository;

    public AdminPreferenceService(PreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeePreferenceResponse> listPreferences(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return preferenceRepository.searchForAdmin(normalizedStatus, from, to).stream()
                .map(EmployeePreferenceResponse::from)
                .toList();
    }

    @Transactional
    public EmployeePreferenceResponse approvePreference(CurrentAccountPrincipal principal, Long preferenceId) {
        return reviewPreference(preferenceId, APPROVED);
    }

    @Transactional
    public EmployeePreferenceResponse rejectPreference(CurrentAccountPrincipal principal, Long preferenceId) {
        return reviewPreference(preferenceId, REJECTED);
    }

    private EmployeePreferenceResponse reviewPreference(Long preferenceId, String status) {
        Preference preference = preferenceRepository.findByIdForAdminReview(preferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preference is not pending");
        }
        preference.setStatus(status);
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim();
        if ("ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference request");
    }
}

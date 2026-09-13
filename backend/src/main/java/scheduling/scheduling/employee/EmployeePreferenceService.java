package scheduling.scheduling.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Service
public class EmployeePreferenceService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";
    private static final String WANT = "WANT";
    private static final String AVOID = "AVOID";
    private static final String ACTIVE = "ACTIVE";

    private final EmployeeAccess employeeAccess;
    private final PreferenceRepository preferenceRepository;
    private final ShiftTypeRepository shiftTypeRepository;

    public EmployeePreferenceService(
            EmployeeAccess employeeAccess,
            PreferenceRepository preferenceRepository,
            ShiftTypeRepository shiftTypeRepository) {
        this.employeeAccess = employeeAccess;
        this.preferenceRepository = preferenceRepository;
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeePreferenceResponse> listPreferences(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return preferenceRepository.searchForEmployee(employee.getId(), normalizedStatus, from, to).stream()
                .map(EmployeePreferenceResponse::from)
                .toList();
    }

    @Transactional
    public EmployeePreferenceResponse createPreference(CurrentAccountPrincipal principal, CreatePreferenceRequest request) {
        Employee employee = employeeAccess.requireActiveEmployeeForUpdate(principal);
        String preferenceType = normalizePreferenceType(request.preferenceType());
        ShiftType shiftType = resolveShiftType(request.shiftTypeId());
        Long shiftTypeId = shiftType == null ? null : shiftType.getId();
        if (preferenceRepository.existsActiveDuplicate(employee.getId(), request.preferenceDate(), shiftTypeId, preferenceType, null)) {
            throw invalidRequest();
        }
        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(request.preferenceDate())
                .preferenceType(preferenceType)
                .priority(request.priority())
                .note(normalizeNote(request.note()))
                .status(PENDING)
                .build();
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    @Transactional
    public EmployeePreferenceResponse updatePreference(CurrentAccountPrincipal principal, Long preferenceId, UpdatePreferenceRequest request) {
        Employee employee = employeeAccess.requireActiveEmployeeForUpdate(principal);
        Preference preference = preferenceRepository.findByIdAndEmployeeIdForUpdate(preferenceId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw invalidRequest();
        }
        String preferenceType = normalizePreferenceType(request.preferenceType());
        ShiftType shiftType = resolveShiftType(request.shiftTypeId());
        Long shiftTypeId = shiftType == null ? null : shiftType.getId();
        if (preferenceRepository.existsActiveDuplicate(employee.getId(), request.preferenceDate(), shiftTypeId, preferenceType, preferenceId)) {
            throw invalidRequest();
        }
        preference.setPreferenceDate(request.preferenceDate());
        preference.setPreferenceType(preferenceType);
        preference.setShiftType(shiftType);
        preference.setPriority(request.priority());
        preference.setNote(normalizeNote(request.note()));
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    @Transactional
    public void cancelPreference(CurrentAccountPrincipal principal, Long preferenceId) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        Preference preference = preferenceRepository.findByIdAndEmployeeIdForUpdate(preferenceId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw invalidRequest();
        }
        preference.setStatus(CANCELLED);
        preferenceRepository.save(preference);
    }

    private ShiftType resolveShiftType(Long shiftTypeId) {
        if (shiftTypeId == null) {
            return null;
        }
        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(EmployeePreferenceService::invalidRequest);
        if (!ACTIVE.equals(shiftType.getStatus())) {
            throw invalidRequest();
        }
        return shiftType;
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizePreferenceType(String preferenceType) {
        String normalized = preferenceType.trim();
        if (!WANT.equals(normalized) && !AVOID.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeNote(String note) {
        return StringUtils.hasText(note) ? note.trim() : null;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference request");
    }
}

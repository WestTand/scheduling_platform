package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;
import scheduling.scheduling.auth.DuplicateResourceException;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Service
public class AdminShiftTypeService {

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String REGULAR = "REGULAR";
    private static final String OVERTIME = "OVERTIME";
    private static final String BATHING = "BATHING";
    private static final String ON_CALL = "ON_CALL";

    private final ShiftTypeRepository shiftTypeRepository;

    public AdminShiftTypeService(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminShiftTypeResponse> listShiftTypes(String query, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? validateStatus(status.trim()) : null;
        return shiftTypeRepository.search(normalizedQuery, normalizedStatus).stream()
                .map(AdminShiftTypeResponse::from)
                .toList();
    }

    @Transactional
    public AdminShiftTypeResponse createShiftType(CreateShiftTypeRequest request) {
        validateStaffRange(request.minStaff(), request.maxStaff());
        String quotaCategory = validateQuotaCategory(request.quotaCategory(), false);
        if (shiftTypeRepository.existsByShiftName(request.shiftName())) {
            throw new DuplicateResourceException("Shift type name already exists");
        }
        String shiftCode = ShiftTypeRules.validateShiftCode(request.shiftCode());
        if (shiftTypeRepository.existsByShiftCode(shiftCode)) {
            throw new DuplicateResourceException("Shift code already exists");
        }
        BigDecimal duration = ShiftTypeRules.validateDuration(request.durationHours());
        boolean appliesOnHolidays = Boolean.TRUE.equals(request.appliesOnHolidays());
        String weekdays = ShiftTypeRules.normalizeWeekdays(request.applicableWeekdays(), appliesOnHolidays);
        BigDecimal multiplier = ShiftTypeRules.normalizeMultiplier(request.penaltyWeightMultiplier());

        ShiftType shiftType = ShiftType.builder()
                .shiftName(request.shiftName())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .durationHours(duration)
                .overnight(Boolean.TRUE.equals(request.overnight()))
                .heavy(Boolean.TRUE.equals(request.heavy()))
                .minStaff(request.minStaff())
                .maxStaff(request.maxStaff())
                .quotaCategory(quotaCategory)
                .status(ACTIVE)
                .shiftCode(shiftCode)
                .applicableWeekdays(weekdays)
                .appliesOnHolidays(appliesOnHolidays)
                .penaltyWeightMultiplier(multiplier)
                .build();
        return AdminShiftTypeResponse.from(shiftTypeRepository.save(shiftType));
    }

    @Transactional
    public AdminShiftTypeResponse updateShiftType(Long shiftTypeId, UpdateShiftTypeRequest request) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        validateStaffRange(request.minStaff(), request.maxStaff());
        String status = validateStatus(request.status().trim());
        String quotaCategory = validateQuotaCategory(request.quotaCategory(), true);
        if (shiftTypeRepository.existsByShiftNameAndIdNot(request.shiftName(), shiftTypeId)) {
            throw new DuplicateResourceException("Shift type name already exists");
        }
        String shiftCode = ShiftTypeRules.validateShiftCode(request.shiftCode());
        if (shiftTypeRepository.existsByShiftCodeAndIdNot(shiftCode, shiftTypeId)) {
            throw new DuplicateResourceException("Shift code already exists");
        }
        BigDecimal duration = ShiftTypeRules.validateDuration(request.durationHours());
        boolean appliesOnHolidays = Boolean.TRUE.equals(request.appliesOnHolidays());
        String weekdays = ShiftTypeRules.normalizeWeekdays(request.applicableWeekdays(), appliesOnHolidays);
        BigDecimal multiplier = ShiftTypeRules.normalizeMultiplier(request.penaltyWeightMultiplier());

        shiftType.setShiftName(request.shiftName());
        shiftType.setStartTime(request.startTime());
        shiftType.setEndTime(request.endTime());
        shiftType.setDurationHours(duration);
        shiftType.setOvernight(request.overnight());
        shiftType.setHeavy(request.heavy());
        shiftType.setMinStaff(request.minStaff());
        shiftType.setMaxStaff(request.maxStaff());
        shiftType.setQuotaCategory(quotaCategory);
        shiftType.setStatus(status);
        shiftType.setShiftCode(shiftCode);
        shiftType.setApplicableWeekdays(weekdays);
        shiftType.setAppliesOnHolidays(appliesOnHolidays);
        shiftType.setPenaltyWeightMultiplier(multiplier);
        return AdminShiftTypeResponse.from(shiftTypeRepository.save(shiftType));
    }

    @Transactional
    public void lockShiftType(Long shiftTypeId) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        shiftType.setStatus(INACTIVE);
        shiftTypeRepository.save(shiftType);
    }

    @Transactional
    public void unlockShiftType(Long shiftTypeId) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        shiftType.setStatus(ACTIVE);
        shiftTypeRepository.save(shiftType);
    }

    private ShiftType findShiftType(Long shiftTypeId) {
        return shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(ShiftTypeNotFoundException::new);
    }

    private static void validateStaffRange(Integer minStaff, Integer maxStaff) {
        if (minStaff != null && maxStaff != null && maxStaff < minStaff) {
            throw invalidRequest();
        }
    }

    private static String validateStatus(String status) {
        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw invalidRequest();
        }
        return status;
    }

    private static String validateQuotaCategory(String quotaCategory, boolean required) {
        if (!StringUtils.hasText(quotaCategory)) {
            if (required) {
                throw invalidRequest();
            }
            return REGULAR;
        }
        String normalized = quotaCategory.trim();
        if (!REGULAR.equals(normalized) && !OVERTIME.equals(normalized)
                && !BATHING.equals(normalized) && !ON_CALL.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift type request");
    }
}

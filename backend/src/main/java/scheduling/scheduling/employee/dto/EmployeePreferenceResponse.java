package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import scheduling.scheduling.entity.Preference;

public record EmployeePreferenceResponse(
        Long id,
        LocalDate preferenceDate,
        String preferenceType,
        Long shiftTypeId,
        String shiftName,
        Integer priority,
        String note,
        String status) {

    public static EmployeePreferenceResponse from(Preference preference) {
        Long shiftTypeId = preference.getShiftType() == null ? null : preference.getShiftType().getId();
        String shiftName = preference.getShiftType() == null ? null : preference.getShiftType().getShiftName();
        return new EmployeePreferenceResponse(
                preference.getId(),
                preference.getPreferenceDate(),
                preference.getPreferenceType(),
                shiftTypeId,
                shiftName,
                preference.getPriority(),
                preference.getNote(),
                preference.getStatus());
    }
}

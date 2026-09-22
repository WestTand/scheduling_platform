package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import scheduling.scheduling.entity.Preference;

public record EmployeePreferenceResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate preferenceDate,
        String preferenceType,
        Long shiftTypeId,
        String shiftName,
        String shiftCode,
        Integer priority,
        String note,
        String status) {

    public static EmployeePreferenceResponse from(Preference preference) {
        Long employeeId = preference.getEmployee() == null ? null : preference.getEmployee().getId();
        String employeeCode = preference.getEmployee() == null ? null : preference.getEmployee().getEmployeeCode();
        String employeeName = preference.getEmployee() == null ? null : preference.getEmployee().getFullName();
        Long shiftTypeId = preference.getShiftType() == null ? null : preference.getShiftType().getId();
        String shiftName = preference.getShiftType() == null ? null : preference.getShiftType().getShiftName();
        String shiftCode = preference.getShiftType() == null ? null : preference.getShiftType().getShiftCode();
        return new EmployeePreferenceResponse(
                preference.getId(),
                employeeId,
                employeeCode,
                employeeName,
                preference.getPreferenceDate(),
                preference.getPreferenceType(),
                shiftTypeId,
                shiftName,
                shiftCode,
                preference.getPriority(),
                preference.getNote(),
                preference.getStatus());
    }
}

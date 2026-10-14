package scheduling.scheduling.employee.dto;

import java.time.LocalTime;
import scheduling.scheduling.entity.ShiftType;

public record EmployeeShiftTypeResponse(
        Long id,
        String shiftCode,
        String shiftName,
        LocalTime startTime,
        LocalTime endTime,
        String quotaCategory) {

    public static EmployeeShiftTypeResponse from(ShiftType shiftType) {
        return new EmployeeShiftTypeResponse(
                shiftType.getId(),
                shiftType.getShiftCode(),
                shiftType.getShiftName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                shiftType.getQuotaCategory());
    }
}

package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import scheduling.scheduling.entity.ShiftType;

public record AdminShiftTypeResponse(
        Long id,
        String shiftName,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal durationHours,
        Boolean overnight,
        Boolean heavy,
        Integer minStaff,
        Integer maxStaff,
        String quotaCategory,
        String status) {

    public static AdminShiftTypeResponse from(ShiftType shiftType) {
        return new AdminShiftTypeResponse(
                shiftType.getId(),
                shiftType.getShiftName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                shiftType.getDurationHours(),
                shiftType.getOvernight(),
                shiftType.getHeavy(),
                shiftType.getMinStaff(),
                shiftType.getMaxStaff(),
                shiftType.getQuotaCategory(),
                shiftType.getStatus());
    }
}

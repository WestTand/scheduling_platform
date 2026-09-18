package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import scheduling.scheduling.admin.WorkShiftWindowService;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;

public record AdminWorkShiftResponse(
        Long id,
        Long periodId,
        String periodName,
        Long shiftTypeId,
        String shiftName,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        LocalDate workDate,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Integer workMinutes,
        Boolean isTransitionShift,
        Integer requiredStaff,
        String note) {

    public static AdminWorkShiftResponse from(WorkShift workShift) {
        SchedulePeriod period = workShift.getSchedulePeriod();
        ShiftType shiftType = workShift.getShiftType();
        WorkShiftWindowService.Window window = new WorkShiftWindowService().resolve(workShift);
        return new AdminWorkShiftResponse(
                workShift.getId(),
                period == null ? null : period.getId(),
                period == null ? null : period.getPeriodName(),
                shiftType == null ? null : shiftType.getId(),
                shiftType == null ? null : shiftType.getShiftName(),
                shiftType == null ? null : shiftType.getStartTime(),
                shiftType == null ? null : shiftType.getEndTime(),
                workShift.getWorkDate(),
                window.startsAt(),
                window.endsAt(),
                window.workMinutes(),
                window.transitionShift(),
                workShift.getRequiredStaff(),
                workShift.getNote());
    }
}

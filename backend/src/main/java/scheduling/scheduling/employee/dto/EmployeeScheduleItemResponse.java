package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import scheduling.scheduling.admin.WorkShiftWindowService;
import scheduling.scheduling.entity.ShiftAssignment;

public record EmployeeScheduleItemResponse(
        Long assignmentId,
        String assignmentStatus,
        String assignedSource,
        Boolean locked,
        LocalDate workDate,
        Long shiftTypeId,
        String shiftTypeName,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Integer workMinutes,
        Boolean isTransitionShift,
        Long periodId,
        String periodName,
        String periodStatus) {

    public static EmployeeScheduleItemResponse from(ShiftAssignment assignment) {
        WorkShiftWindowService.Window window = new WorkShiftWindowService().resolve(assignment.getWorkShift());
        return new EmployeeScheduleItemResponse(
                assignment.getId(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                assignment.getLocked(),
                assignment.getWorkShift().getWorkDate(),
                assignment.getWorkShift().getShiftType().getId(),
                assignment.getWorkShift().getShiftType().getShiftName(),
                assignment.getWorkShift().getShiftType().getStartTime(),
                assignment.getWorkShift().getShiftType().getEndTime(),
                window.startsAt(),
                window.endsAt(),
                window.workMinutes(),
                window.transitionShift(),
                assignment.getWorkShift().getSchedulePeriod().getId(),
                assignment.getWorkShift().getSchedulePeriod().getPeriodName(),
                assignment.getWorkShift().getSchedulePeriod().getStatus());
    }
}

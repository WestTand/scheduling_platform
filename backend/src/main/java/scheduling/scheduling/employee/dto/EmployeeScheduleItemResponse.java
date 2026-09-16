package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import java.time.LocalTime;
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
        Long periodId,
        String periodName,
        String periodStatus) {

    public static EmployeeScheduleItemResponse from(ShiftAssignment assignment) {
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
                assignment.getWorkShift().getSchedulePeriod().getId(),
                assignment.getWorkShift().getSchedulePeriod().getPeriodName(),
                assignment.getWorkShift().getSchedulePeriod().getStatus());
    }
}

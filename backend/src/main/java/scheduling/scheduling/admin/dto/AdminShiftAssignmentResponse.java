package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.WorkShift;

public record AdminShiftAssignmentResponse(
        Long id,
        Long workShiftId,
        Long periodId,
        Long employeeId,
        String employeeCode,
        String fullName,
        String assignmentStatus,
        String assignedSource,
        Boolean locked) {

    public static AdminShiftAssignmentResponse from(ShiftAssignment assignment) {
        WorkShift workShift = assignment.getWorkShift();
        SchedulePeriod period = workShift == null ? null : workShift.getSchedulePeriod();
        Employee employee = assignment.getEmployee();
        return new AdminShiftAssignmentResponse(
                assignment.getId(),
                workShift == null ? null : workShift.getId(),
                period == null ? null : period.getId(),
                employee == null ? null : employee.getId(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                assignment.getLocked());
    }
}

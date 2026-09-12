package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import scheduling.scheduling.entity.LeaveRequest;

public record EmployeeLeaveRequestResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status) {

    public static EmployeeLeaveRequestResponse from(LeaveRequest leaveRequest) {
        return new EmployeeLeaveRequestResponse(
                leaveRequest.getId(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus());
    }
}

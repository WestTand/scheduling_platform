package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;

public record AdminLeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        String reviewNote,
        Long reviewedByAccountId,
        String reviewedByUsername,
        LocalDateTime reviewedAt) {

    public static AdminLeaveRequestResponse from(LeaveRequest leaveRequest) {
        Employee employee = leaveRequest.getEmployee();
        Account reviewedBy = leaveRequest.getReviewedBy();
        return new AdminLeaveRequestResponse(
                leaveRequest.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus(),
                leaveRequest.getReviewNote(),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getUsername(),
                leaveRequest.getReviewedAt());
    }
}

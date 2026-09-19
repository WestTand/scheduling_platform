package scheduling.scheduling.employee.dto;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;

public record SwapRequestResponse(
        Long id,
        Long requesterAssignmentId,
        EmployeeSummary requesterEmployee,
        Long targetAssignmentId,
        EmployeeSummary targetEmployee,
        String reason,
        String status,
        Long reviewedByAccountId,
        String reviewedByUsername,
        LocalDateTime reviewedAt) {

    public record EmployeeSummary(Long id, String employeeCode, String fullName) {
    }

    public static SwapRequestResponse from(SwapRequest swapRequest) {
        ShiftAssignment requesterAssignment = swapRequest.getRequesterAssignment();
        Employee requester = requesterAssignment.getEmployee();
        Employee target = swapRequest.getTargetEmployee();
        Account reviewedBy = swapRequest.getReviewedBy();
        return new SwapRequestResponse(
                swapRequest.getId(),
                requesterAssignment.getId(),
                new EmployeeSummary(requester.getId(), requester.getEmployeeCode(), requester.getFullName()),
                swapRequest.getTargetAssignment() == null ? null : swapRequest.getTargetAssignment().getId(),
                new EmployeeSummary(target.getId(), target.getEmployeeCode(), target.getFullName()),
                swapRequest.getReason(),
                swapRequest.getStatus(),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getUsername(),
                swapRequest.getReviewedAt());
    }
}

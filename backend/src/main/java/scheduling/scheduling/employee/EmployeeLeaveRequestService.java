package scheduling.scheduling.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.LeaveRequestRepository;

@Service
public class EmployeeLeaveRequestService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final EmployeeAccess employeeAccess;
    private final LeaveRequestRepository leaveRequestRepository;

    public EmployeeLeaveRequestService(EmployeeAccess employeeAccess, LeaveRequestRepository leaveRequestRepository) {
        this.employeeAccess = employeeAccess;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeLeaveRequestResponse> listLeaveRequests(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return leaveRequestRepository.searchForEmployee(employee.getId(), normalizedStatus, from, to).stream()
                .map(EmployeeLeaveRequestResponse::from)
                .toList();
    }

    @Transactional
    public EmployeeLeaveRequestResponse createLeaveRequest(CurrentAccountPrincipal principal, CreateLeaveRequestRequest request) {
        Employee employee = employeeAccess.requireActiveEmployeeForUpdate(principal);
        validateDateRange(request.startDate(), request.endDate());
        if (leaveRequestRepository.existsOverlappingActiveRequest(employee.getId(), request.startDate(), request.endDate())) {
            throw duplicateLeaveRequest();
        }
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null)
                .status(PENDING)
                .build();
        return EmployeeLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public void cancelLeaveRequest(CurrentAccountPrincipal principal, Long leaveRequestId) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        LeaveRequest leaveRequest = leaveRequestRepository.findByIdAndEmployeeIdForUpdate(leaveRequestId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!PENDING.equals(leaveRequest.getStatus())) {
            throw invalidRequest();
        }
        leaveRequest.setStatus(CANCELLED);
        leaveRequestRepository.save(leaveRequest);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw invalidRequest();
        }
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave request");
    }

    private static ResponseStatusException duplicateLeaveRequest() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Đã có yêu cầu nghỉ chờ duyệt hoặc đã duyệt trong khoảng ngày này.");
    }
}

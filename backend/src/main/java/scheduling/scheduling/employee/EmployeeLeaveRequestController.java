package scheduling.scheduling.employee;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;

@RestController
@RequestMapping("/api/employee/leave-requests")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeLeaveRequestController {

    private final EmployeeLeaveRequestService employeeLeaveRequestService;

    public EmployeeLeaveRequestController(EmployeeLeaveRequestService employeeLeaveRequestService) {
        this.employeeLeaveRequestService = employeeLeaveRequestService;
    }

    @GetMapping
    ResponseEntity<List<EmployeeLeaveRequestResponse>> listLeaveRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeeLeaveRequestService.listLeaveRequests(principal, status, from, to));
    }

    @PostMapping
    ResponseEntity<EmployeeLeaveRequestResponse> createLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreateLeaveRequestRequest request) {
        return ResponseEntity.status(201).body(employeeLeaveRequestService.createLeaveRequest(principal, request));
    }

    @PostMapping("/{leaveRequestId}/cancel")
    ResponseEntity<Void> cancelLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId) {
        employeeLeaveRequestService.cancelLeaveRequest(principal, leaveRequestId);
        return ResponseEntity.noContent().build();
    }
}

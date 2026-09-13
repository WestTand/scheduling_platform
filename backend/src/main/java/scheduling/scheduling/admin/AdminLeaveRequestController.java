package scheduling.scheduling.admin;

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
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/leave-requests")
@PreAuthorize("hasRole('MANAGER')")
public class AdminLeaveRequestController {

    private final AdminLeaveRequestService adminLeaveRequestService;

    public AdminLeaveRequestController(AdminLeaveRequestService adminLeaveRequestService) {
        this.adminLeaveRequestService = adminLeaveRequestService;
    }

    @GetMapping
    ResponseEntity<List<AdminLeaveRequestResponse>> listLeaveRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminLeaveRequestService.listLeaveRequests(principal, employeeId, status, from, to));
    }

    @PostMapping("/{leaveRequestId}/approve")
    ResponseEntity<AdminLeaveRequestResponse> approveLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId,
            @Valid @RequestBody(required = false) ReviewLeaveRequestRequest request) {
        return ResponseEntity.ok(adminLeaveRequestService.approveLeaveRequest(
                principal, leaveRequestId, defaultRequest(request)));
    }

    @PostMapping("/{leaveRequestId}/reject")
    ResponseEntity<AdminLeaveRequestResponse> rejectLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId,
            @Valid @RequestBody(required = false) ReviewLeaveRequestRequest request) {
        return ResponseEntity.ok(adminLeaveRequestService.rejectLeaveRequest(
                principal, leaveRequestId, defaultRequest(request)));
    }

    private static ReviewLeaveRequestRequest defaultRequest(ReviewLeaveRequestRequest request) {
        return request == null ? new ReviewLeaveRequestRequest(null) : request;
    }
}

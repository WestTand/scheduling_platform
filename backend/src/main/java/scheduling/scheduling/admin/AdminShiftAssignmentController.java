package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MANAGER')")
public class AdminShiftAssignmentController {

    private final AdminShiftAssignmentService adminShiftAssignmentService;

    public AdminShiftAssignmentController(AdminShiftAssignmentService adminShiftAssignmentService) {
        this.adminShiftAssignmentService = adminShiftAssignmentService;
    }

    @PostMapping("/work-shifts/{workShiftId}/assignments")
    ResponseEntity<AdminShiftAssignmentResponse> createAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long workShiftId,
            @Valid @RequestBody CreateShiftAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminShiftAssignmentService.createAssignment(principal, workShiftId, request));
    }

    @GetMapping("/schedule-periods/{periodId}/schedule")
    ResponseEntity<AdminPeriodScheduleResponse> getSchedule(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminShiftAssignmentService.getSchedule(principal, periodId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    ResponseEntity<Void> deleteAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        adminShiftAssignmentService.deleteAssignment(principal, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/assignments/{assignmentId}/lock")
    ResponseEntity<AdminShiftAssignmentResponse> lockAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(adminShiftAssignmentService.lockAssignment(principal, assignmentId));
    }

    @PatchMapping("/assignments/{assignmentId}/unlock")
    ResponseEntity<AdminShiftAssignmentResponse> unlockAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(adminShiftAssignmentService.unlockAssignment(principal, assignmentId));
    }
}

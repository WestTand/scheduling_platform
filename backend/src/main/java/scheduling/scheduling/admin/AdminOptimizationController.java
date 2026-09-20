package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.OptimizeScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/schedule-periods")
@PreAuthorize("hasRole('MANAGER')")
public class AdminOptimizationController {

    private final AdminOptimizationService adminOptimizationService;

    public AdminOptimizationController(AdminOptimizationService adminOptimizationService) {
        this.adminOptimizationService = adminOptimizationService;
    }

    @PostMapping("/{periodId}/optimize")
    ResponseEntity<OptimizeScheduleResponse> optimize(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody(required = false) OptimizeScheduleRequest request) {
        return ResponseEntity.ok(adminOptimizationService.optimize(principal, periodId, request));
    }
}

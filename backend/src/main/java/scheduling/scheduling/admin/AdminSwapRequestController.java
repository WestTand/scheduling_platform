package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;

@RestController
@RequestMapping("/api/admin/swap-requests")
@PreAuthorize("hasRole('MANAGER')")
public class AdminSwapRequestController {

    private final AdminSwapRequestService adminSwapRequestService;

    public AdminSwapRequestController(AdminSwapRequestService adminSwapRequestService) {
        this.adminSwapRequestService = adminSwapRequestService;
    }

    @GetMapping
    ResponseEntity<List<SwapRequestResponse>> listSwapRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminSwapRequestService.listSwapRequests(principal, status));
    }

    @PostMapping("/{swapRequestId}/approve")
    ResponseEntity<SwapRequestResponse> approveSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(adminSwapRequestService.approveSwapRequest(principal, swapRequestId));
    }

    @PostMapping("/{swapRequestId}/reject")
    ResponseEntity<SwapRequestResponse> rejectSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(adminSwapRequestService.rejectSwapRequest(principal, swapRequestId));
    }
}

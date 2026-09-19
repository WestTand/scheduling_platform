package scheduling.scheduling.employee;

import jakarta.validation.Valid;
import java.util.List;
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
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;

@RestController
@RequestMapping("/api/employee/swap-requests")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeSwapRequestController {

    private final EmployeeSwapRequestService employeeSwapRequestService;

    public EmployeeSwapRequestController(EmployeeSwapRequestService employeeSwapRequestService) {
        this.employeeSwapRequestService = employeeSwapRequestService;
    }

    @GetMapping
    ResponseEntity<List<SwapRequestResponse>> listSwapRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(employeeSwapRequestService.listSwapRequests(principal, status));
    }

    @PostMapping
    ResponseEntity<SwapRequestResponse> createSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreateSwapRequestRequest request) {
        return ResponseEntity.status(201).body(employeeSwapRequestService.createSwapRequest(principal, request));
    }

    @PostMapping("/{swapRequestId}/cancel")
    ResponseEntity<SwapRequestResponse> cancelSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId) {
        return ResponseEntity.ok(employeeSwapRequestService.cancelSwapRequest(principal, swapRequestId));
    }

    @PostMapping("/{swapRequestId}/respond")
    ResponseEntity<SwapRequestResponse> respondSwapRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long swapRequestId,
            @Valid @RequestBody RespondSwapRequestRequest request) {
        return ResponseEntity.ok(employeeSwapRequestService.respondSwapRequest(principal, swapRequestId, request));
    }
}

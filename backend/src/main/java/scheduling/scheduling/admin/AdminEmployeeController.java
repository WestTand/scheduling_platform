package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.admin.dto.UpdateEmployeeRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/employees")
public class AdminEmployeeController {

    private final AdminEmployeeService adminEmployeeService;

    public AdminEmployeeController(AdminEmployeeService adminEmployeeService) {
        this.adminEmployeeService = adminEmployeeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<List<AdminEmployeeResponse>> listEmployees(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminEmployeeService.listEmployees(query, status));
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminEmployeeResponse> getEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(adminEmployeeService.getEmployee(employeeId));
    }

    @PutMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminEmployeeResponse> updateEmployee(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(adminEmployeeService.updateEmployee(principal, employeeId, request));
    }

    @PostMapping("/{employeeId}/lock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> lockEmployee(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long employeeId) {
        adminEmployeeService.lockEmployee(principal, employeeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{employeeId}/unlock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> unlockEmployee(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long employeeId) {
        adminEmployeeService.unlockEmployee(principal, employeeId);
        return ResponseEntity.noContent().build();
    }
}

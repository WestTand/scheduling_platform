package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;

@RestController
@RequestMapping("/api/admin/preferences")
@PreAuthorize("hasRole('MANAGER')")
public class AdminPreferenceController {

    private final AdminPreferenceService adminPreferenceService;

    public AdminPreferenceController(AdminPreferenceService adminPreferenceService) {
        this.adminPreferenceService = adminPreferenceService;
    }

    @GetMapping
    ResponseEntity<List<EmployeePreferenceResponse>> listPreferences(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminPreferenceService.listPreferences(principal, status, from, to));
    }

    @PostMapping("/{preferenceId}/approve")
    ResponseEntity<EmployeePreferenceResponse> approvePreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        return ResponseEntity.ok(adminPreferenceService.approvePreference(principal, preferenceId));
    }

    @PostMapping("/{preferenceId}/reject")
    ResponseEntity<EmployeePreferenceResponse> rejectPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        return ResponseEntity.ok(adminPreferenceService.rejectPreference(principal, preferenceId));
    }
}

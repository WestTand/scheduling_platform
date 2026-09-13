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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;

@RestController
@RequestMapping("/api/employee/preferences")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeePreferenceController {

    private final EmployeePreferenceService employeePreferenceService;

    public EmployeePreferenceController(EmployeePreferenceService employeePreferenceService) {
        this.employeePreferenceService = employeePreferenceService;
    }

    @GetMapping
    ResponseEntity<List<EmployeePreferenceResponse>> listPreferences(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeePreferenceService.listPreferences(principal, status, from, to));
    }

    @PostMapping
    ResponseEntity<EmployeePreferenceResponse> createPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreatePreferenceRequest request) {
        return ResponseEntity.status(201).body(employeePreferenceService.createPreference(principal, request));
    }

    @PutMapping("/{preferenceId}")
    ResponseEntity<EmployeePreferenceResponse> updatePreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(employeePreferenceService.updatePreference(principal, preferenceId, request));
    }

    @PostMapping("/{preferenceId}/cancel")
    ResponseEntity<Void> cancelPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        employeePreferenceService.cancelPreference(principal, preferenceId);
        return ResponseEntity.noContent().build();
    }
}

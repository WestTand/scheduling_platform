package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MANAGER')")
public class AdminWorkShiftController {

    private final AdminWorkShiftService adminWorkShiftService;

    public AdminWorkShiftController(AdminWorkShiftService adminWorkShiftService) {
        this.adminWorkShiftService = adminWorkShiftService;
    }

    @PostMapping("/schedule-periods/{periodId}/work-shifts")
    ResponseEntity<AdminWorkShiftResponse> createWorkShift(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody CreateWorkShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminWorkShiftService.createWorkShift(principal, periodId, request));
    }

    @GetMapping("/schedule-periods/{periodId}/work-shifts")
    ResponseEntity<List<AdminWorkShiftResponse>> listWorkShifts(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long shiftTypeId) {
        return ResponseEntity.ok(adminWorkShiftService.listWorkShifts(principal, periodId, date, shiftTypeId));
    }

    @GetMapping("/work-shifts/{workShiftId}")
    ResponseEntity<AdminWorkShiftResponse> getWorkShift(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long workShiftId) {
        return ResponseEntity.ok(adminWorkShiftService.getWorkShift(principal, workShiftId));
    }
}

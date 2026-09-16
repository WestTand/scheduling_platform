package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/schedule-periods")
@PreAuthorize("hasRole('MANAGER')")
public class AdminSchedulePeriodController {

    private static final String DRAFT = "DRAFT";

    private final AdminSchedulePeriodService adminSchedulePeriodService;

    public AdminSchedulePeriodController(AdminSchedulePeriodService adminSchedulePeriodService) {
        this.adminSchedulePeriodService = adminSchedulePeriodService;
    }

    @PostMapping
    ResponseEntity<AdminSchedulePeriodResponse> createSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody SchedulePeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminSchedulePeriodService.createSchedulePeriod(principal, request));
    }

    @GetMapping
    ResponseEntity<List<AdminSchedulePeriodResponse>> listSchedulePeriods(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminSchedulePeriodService.listSchedulePeriods(principal, listStatus(status), from, to));
    }

    @GetMapping("/{periodId}")
    ResponseEntity<AdminSchedulePeriodResponse> getSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.getSchedulePeriod(principal, periodId));
    }

    @PutMapping("/{periodId}")
    ResponseEntity<AdminSchedulePeriodResponse> updateSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody SchedulePeriodRequest request) {
        return ResponseEntity.ok(adminSchedulePeriodService.updateSchedulePeriod(principal, periodId, request));
    }

    @PatchMapping("/{periodId}/approve")
    ResponseEntity<AdminSchedulePeriodResponse> approveSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.approveSchedulePeriod(principal, periodId));
    }

    @PatchMapping("/{periodId}/publish")
    ResponseEntity<AdminSchedulePeriodResponse> publishSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.publishSchedulePeriod(principal, periodId));
    }

    @PatchMapping("/{periodId}/cancel")
    ResponseEntity<AdminSchedulePeriodResponse> cancelSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.cancelSchedulePeriod(principal, periodId));
    }

    private static String listStatus(String status) {
        return StringUtils.hasText(status) ? status : DRAFT;
    }
}

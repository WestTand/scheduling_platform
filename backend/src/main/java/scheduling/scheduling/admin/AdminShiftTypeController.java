package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;

@RestController
@RequestMapping("/api/admin/shift-types")
public class AdminShiftTypeController {

    private final AdminShiftTypeService adminShiftTypeService;

    public AdminShiftTypeController(AdminShiftTypeService adminShiftTypeService) {
        this.adminShiftTypeService = adminShiftTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<List<AdminShiftTypeResponse>> listShiftTypes(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminShiftTypeService.listShiftTypes(query, status));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminShiftTypeResponse> createShiftType(@Valid @RequestBody CreateShiftTypeRequest request) {
        return ResponseEntity.status(201).body(adminShiftTypeService.createShiftType(request));
    }

    @PutMapping("/{shiftTypeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminShiftTypeResponse> updateShiftType(
            @PathVariable Long shiftTypeId,
            @Valid @RequestBody UpdateShiftTypeRequest request) {
        return ResponseEntity.ok(adminShiftTypeService.updateShiftType(shiftTypeId, request));
    }

    @PostMapping("/{shiftTypeId}/lock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> lockShiftType(@PathVariable Long shiftTypeId) {
        adminShiftTypeService.lockShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{shiftTypeId}/unlock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> unlockShiftType(@PathVariable Long shiftTypeId) {
        adminShiftTypeService.unlockShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }
}

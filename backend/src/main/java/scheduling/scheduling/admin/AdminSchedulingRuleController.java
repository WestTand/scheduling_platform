package scheduling.scheduling.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminSchedulingRulesResponse;

@RestController
@RequestMapping("/api/admin/scheduling-rules")
public class AdminSchedulingRuleController {

    private final AdminSchedulingRuleService adminSchedulingRuleService;

    public AdminSchedulingRuleController(AdminSchedulingRuleService adminSchedulingRuleService) {
        this.adminSchedulingRuleService = adminSchedulingRuleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminSchedulingRulesResponse> getRules() {
        return ResponseEntity.ok(adminSchedulingRuleService.getRules());
    }
}

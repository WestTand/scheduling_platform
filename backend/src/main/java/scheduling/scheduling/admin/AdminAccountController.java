package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminAccountResponse;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<List<AdminAccountResponse>> listAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminAccountService.listAccounts(query, role, status));
    }

    @PostMapping("/{accountId}/lock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> lockAccount(@PathVariable Long accountId) {
        adminAccountService.lockAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/unlock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> unlockAccount(@PathVariable Long accountId) {
        adminAccountService.unlockAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}

package scheduling.scheduling.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.dto.AccountResponse;
import scheduling.scheduling.auth.dto.CreateEmployeeAccountRequest;

@RestController
@RequestMapping("/api/manager/accounts")
public class ManagerAccountController {

    private final ManagerAccountService managerAccountService;

    public ManagerAccountController(ManagerAccountService managerAccountService) {
        this.managerAccountService = managerAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AccountResponse> createEmployeeAccount(@Valid @RequestBody CreateEmployeeAccountRequest request) {
        return ResponseEntity.status(201).body(managerAccountService.createEmployeeAccount(request));
    }

    @PostMapping("/{accountId}/activation/resend")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> resendActivationOtp(@PathVariable Long accountId) {
        managerAccountService.resendActivationOtp(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/password/reset")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> resetPassword(@PathVariable Long accountId) {
        managerAccountService.resetPassword(accountId);
        return ResponseEntity.noContent().build();
    }
}

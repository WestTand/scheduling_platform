package scheduling.scheduling.notification;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/employee/notifications")
@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
public class EmployeeNotificationController {

    private final NotificationService notificationService;

    public EmployeeNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    ResponseEntity<List<NotificationResponse>> listNotifications(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) Boolean read) {
        return ResponseEntity.ok(notificationService.list(principal, read));
    }

    @PatchMapping("/{notificationId}/read")
    ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(principal, notificationId));
    }

    @PatchMapping("/read-all")
    ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CurrentAccountPrincipal principal) {
        notificationService.markAllRead(principal);
        return ResponseEntity.noContent().build();
    }
}

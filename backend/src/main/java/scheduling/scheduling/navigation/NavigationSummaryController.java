package scheduling.scheduling.navigation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/me/navigation-summary")
@PreAuthorize("isAuthenticated()")
public class NavigationSummaryController {

    private final NavigationSummaryService navigationSummaryService;

    public NavigationSummaryController(NavigationSummaryService navigationSummaryService) {
        this.navigationSummaryService = navigationSummaryService;
    }

    @GetMapping
    ResponseEntity<NavigationSummaryResponse> getSummary(@AuthenticationPrincipal CurrentAccountPrincipal principal) {
        return ResponseEntity.ok(navigationSummaryService.getSummary(principal));
    }
}

package scheduling.scheduling.navigation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.NotificationRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class NavigationSummaryService {

    private static final String PENDING = "PENDING";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PreferenceRepository preferenceRepository;
    private final SwapRequestRepository swapRequestRepository;

    public NavigationSummaryService(
            AccountRepository accountRepository,
            NotificationRepository notificationRepository,
            LeaveRequestRepository leaveRequestRepository,
            PreferenceRepository preferenceRepository,
            SwapRequestRepository swapRequestRepository) {
        this.accountRepository = accountRepository;
        this.notificationRepository = notificationRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.preferenceRepository = preferenceRepository;
        this.swapRequestRepository = swapRequestRepository;
    }

    @Transactional(readOnly = true)
    public NavigationSummaryResponse getSummary(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        long unreadNotifications = notificationRepository.countUnreadForRecipient(account.getId());

        if (account.getRole() == AccountRole.MANAGER || account.getRole() == AccountRole.ADMIN) {
            return new NavigationSummaryResponse(
                    leaveRequestRepository.countByStatus(PENDING),
                    preferenceRepository.countByStatus(PENDING),
                    swapRequestRepository.countByStatus(PENDING_APPROVAL),
                    unreadNotifications,
                    0L);
        }

        Employee employee = account.getEmployee();
        if (employee == null) {
            return new NavigationSummaryResponse(0L, 0L, 0L, unreadNotifications, 0L);
        }

        return new NavigationSummaryResponse(
                leaveRequestRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                preferenceRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                swapRequestRepository.countPendingForEmployee(employee.getId()),
                unreadNotifications,
                0L);
    }
}

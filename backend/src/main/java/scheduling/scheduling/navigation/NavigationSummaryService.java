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
    // Phase 9J: employee swap scope covers both PENDING_TARGET and PENDING_APPROVAL
    // (see SwapRequestRepository.countPendingForEmployee); kept as literal in @Query
    // because annotation values cannot reference service constants.
    private static final String PENDING_TARGET = "PENDING_TARGET";

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
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required"));
        long unreadNotifications = notificationRepository.countUnreadForRecipient(account.getId());

        if (account.getRole() == AccountRole.MANAGER || account.getRole() == AccountRole.ADMIN) {
            // Manager queue = actionable only: PENDING_APPROVAL swaps awaiting manager review.
            return new NavigationSummaryResponse(
                    leaveRequestRepository.countByStatus(PENDING),
                    preferenceRepository.countByStatus(PENDING),
                    swapRequestRepository.countByStatus(PENDING_APPROVAL),
                    unreadNotifications,
                    // Phase 9J: pending schedule counts not wired yet
                    0L);
        }

        Employee employee = account.getEmployee();
        if (employee == null) {
            return new NavigationSummaryResponse(
                    0L, 0L, 0L, unreadNotifications,
                    // Phase 9J: pending schedule counts not wired yet
                    0L);
        }

        // Employee scope covers both PENDING_TARGET and PENDING_APPROVAL via countPendingForEmployee.
        return new NavigationSummaryResponse(
                leaveRequestRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                preferenceRepository.countByEmployeeIdAndStatus(employee.getId(), PENDING),
                swapRequestRepository.countPendingForEmployee(employee.getId()),
                unreadNotifications,
                // Phase 9J: pending schedule counts not wired yet
                0L);
    }
}

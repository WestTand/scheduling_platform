package scheduling.scheduling.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.notification.NotificationService;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class AdminSwapRequestService {

    private static final String PENDING_TARGET = "PENDING_TARGET";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final SwapRequestRepository swapRequestRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Autowired
    public AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository,
            AccountRepository accountRepository,
            NotificationService notificationService,
            ActivityLogService activityLogService) {
        this(swapRequestRepository, accountRepository, Clock.systemDefaultZone(), notificationService,
                activityLogService);
    }

    AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository,
            AccountRepository accountRepository,
            Clock clock,
            NotificationService notificationService,
            ActivityLogService activityLogService) {
        this.swapRequestRepository = swapRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> listSwapRequests(CurrentAccountPrincipal principal, String status) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        return swapRequestRepository.findAllDetail(normalizedStatus).stream()
                .map(SwapRequestResponse::from)
                .toList();
    }

    @Transactional
    public SwapRequestResponse approveSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        Account manager = requireManager(principal);
        SwapRequest swapRequest = findDetail(swapRequestId);
        if (!PENDING_APPROVAL.equals(swapRequest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Swap request is not pending approval");
        }
        applyReview(swapRequest, manager, APPROVED);
        recordSwapReview(manager, swapRequest, "APPROVE");
        notifySwapReview(
                swapRequest, "SWAP_APPROVED", "Yêu cầu đổi ca đã được duyệt",
                "Yêu cầu đổi ca của bạn đã được quản lý duyệt.");
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    @Transactional
    public SwapRequestResponse rejectSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        Account manager = requireManager(principal);
        SwapRequest swapRequest = findDetail(swapRequestId);
        if (!PENDING_TARGET.equals(swapRequest.getStatus())
                && !PENDING_APPROVAL.equals(swapRequest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Swap request is not reviewable");
        }
        applyReview(swapRequest, manager, REJECTED);
        recordSwapReview(manager, swapRequest, "REJECT");
        notifySwapReview(
                swapRequest, "SWAP_REJECTED", "Yêu cầu đổi ca đã bị từ chối",
                "Yêu cầu đổi ca của bạn đã bị quản lý từ chối.");
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private SwapRequest findDetail(Long swapRequestId) {
        return swapRequestRepository.findDetailByIdForUpdate(swapRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Swap request not found"));
    }

    private void applyReview(SwapRequest swapRequest, Account manager, String status) {
        swapRequest.setStatus(status);
        swapRequest.setReviewedBy(manager);
        swapRequest.setReviewedAt(LocalDateTime.now(clock));
    }

    private void recordSwapReview(Account manager, SwapRequest swapRequest, String action) {
        activityLogService.record(manager, action, "swap_requests", swapRequest.getId(), null, Map.of(
                "id", swapRequest.getId(),
                "status", swapRequest.getStatus(),
                "requesterAssignmentId", swapRequest.getRequesterAssignment().getId(),
                "targetEmployeeId", swapRequest.getTargetEmployee().getId()));
    }

    private void notifySwapReview(SwapRequest swapRequest, String type, String title, String content) {
        notificationService.notifyAll(
                Arrays.asList(
                        swapRequest.getRequesterAssignment().getEmployee().getAccount(),
                        swapRequest.getTargetEmployee().getAccount()),
                type,
                title,
                content);
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING_TARGET.equals(normalized) && !PENDING_APPROVAL.equals(normalized)
                && !APPROVED.equals(normalized) && !REJECTED.equals(normalized)
                && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid swap review request");
    }
}

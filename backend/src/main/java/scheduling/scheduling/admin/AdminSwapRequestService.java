package scheduling.scheduling.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.notification.NotificationService;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class AdminSwapRequestService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String SWAPPED = "SWAPPED";
    private static final String PENDING_TARGET = "PENDING_TARGET";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";
    private static final String MISSING_TARGET_ASSIGNMENT =
            "Yêu cầu đổi ca chưa có ca đối ứng nên không thể hoán đổi.";
    private static final String MISSING_ASSIGNMENT =
            "Ca cần hoán đổi không còn tồn tại nên không thể hoán đổi.";
    private static final String NOT_PUBLISHED_PERIOD =
            "Ca không còn thuộc kỳ lịch đã công bố nên không thể hoán đổi.";
    private static final String TARGET_OWNER_MISMATCH =
            "Ca đối ứng không thuộc nhân viên được yêu cầu đổi ca.";
    private static final String SAME_WORK_SHIFT =
            "Hai ca cần hoán đổi là cùng một ca nên không thể hoán đổi.";
    private static final String REQUESTER_ASSIGNMENT_ALREADY_SWAPPED =
            "Ca của người gửi đã được đổi bởi một yêu cầu khác nên không thể duyệt yêu cầu này.";
    private static final String TARGET_ASSIGNMENT_ALREADY_SWAPPED =
            "Ca đối ứng đã được đổi bởi một yêu cầu khác nên không thể duyệt yêu cầu này.";
    private static final String CROSS_SHIFT_COLLISION =
            "Hai nhân viên đều đã có ca ở ca của người kia nên không thể hoán đổi.";

    private final SwapRequestRepository swapRequestRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    public AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository,
            AccountRepository accountRepository,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            ShiftAssignmentRepository shiftAssignmentRepository) {
        this(swapRequestRepository, accountRepository, Clock.systemDefaultZone(), notificationService,
                activityLogService, shiftAssignmentRepository);
    }

    AdminSwapRequestService(
            SwapRequestRepository swapRequestRepository,
            AccountRepository accountRepository,
            Clock clock,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            ShiftAssignmentRepository shiftAssignmentRepository) {
        this.swapRequestRepository = swapRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
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
        LockedAssignments locked = lockAssignments(swapRequest);
        ShiftAssignment requesterAssignment = locked.requesterAssignment();
        ShiftAssignment targetAssignment = locked.targetAssignment();
        requirePublishedPeriod(requesterAssignment);
        requirePublishedPeriod(targetAssignment);
        Employee target = requireTargetOwner(swapRequest, targetAssignment);
        requireDifferentWorkShifts(requesterAssignment, targetAssignment);
        requireNotAlreadySwapped(requesterAssignment, targetAssignment);
        Employee requester = requesterAssignment.getEmployee();
        requireNoCrossShiftCollision(requesterAssignment, targetAssignment, requester, target);
        // Must be resolved before the exchange: the assignment references are mutated in place below.
        List<Account> parties = swapParties(requester, target);
        requesterAssignment.setEmployee(target);
        requesterAssignment.setAssignmentStatus(SWAPPED);
        targetAssignment.setEmployee(requester);
        targetAssignment.setAssignmentStatus(SWAPPED);
        persistExchange(requesterAssignment, targetAssignment);
        recordAssignmentSwaps(manager, requesterAssignment, targetAssignment, requester, target);
        applyReview(swapRequest, manager, APPROVED);
        recordSwapReview(manager, swapRequest, "APPROVE");
        notificationService.notifyAll(
                parties, "SWAP_APPROVED", "Yêu cầu đổi ca đã được duyệt",
                "Yêu cầu đổi ca của bạn đã được quản lý duyệt.");
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest), requester);
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
        List<Account> parties = swapParties(
                swapRequest.getRequesterAssignment().getEmployee(), swapRequest.getTargetEmployee());
        applyReview(swapRequest, manager, REJECTED);
        recordSwapReview(manager, swapRequest, "REJECT");
        notificationService.notifyAll(
                parties, "SWAP_REJECTED", "Yêu cầu đổi ca đã bị từ chối",
                "Yêu cầu đổi ca của bạn đã bị quản lý từ chối.");
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    private record LockedAssignments(ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment) {
    }

    private LockedAssignments lockAssignments(SwapRequest swapRequest) {
        ShiftAssignment requesterAssignment = swapRequest.getRequesterAssignment();
        ShiftAssignment targetAssignment = swapRequest.getTargetAssignment();
        if (targetAssignment == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MISSING_TARGET_ASSIGNMENT);
        }
        if (requesterAssignment == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MISSING_ASSIGNMENT);
        }
        List<Long> ids = Stream.of(requesterAssignment.getId(), targetAssignment.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ShiftAssignment> byId = shiftAssignmentRepository.findAllByIdInForUpdate(ids).stream()
                .collect(Collectors.toMap(ShiftAssignment::getId, Function.identity()));
        ShiftAssignment lockedRequester = byId.get(requesterAssignment.getId());
        ShiftAssignment lockedTarget = byId.get(targetAssignment.getId());
        if (lockedRequester == null || lockedTarget == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MISSING_ASSIGNMENT);
        }
        return new LockedAssignments(lockedRequester, lockedTarget);
    }

    private void persistExchange(ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment) {
        try {
            shiftAssignmentRepository.saveAllAndFlush(List.of(requesterAssignment, targetAssignment));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CROSS_SHIFT_COLLISION);
        }
    }

    private void recordAssignmentSwaps(
            Account manager, ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment,
            Employee requester, Employee target) {
        activityLogService.record(manager, "SWAP", "shift_assignments",
                requesterAssignment.getId(), requester.getId(), target.getId());
        activityLogService.record(manager, "SWAP", "shift_assignments",
                targetAssignment.getId(), target.getId(), requester.getId());
    }

    private static Employee requireTargetOwner(SwapRequest swapRequest, ShiftAssignment targetAssignment) {
        Employee declaredTarget = swapRequest.getTargetEmployee();
        if (declaredTarget == null
                || !declaredTarget.getId().equals(targetAssignment.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, TARGET_OWNER_MISMATCH);
        }
        return declaredTarget;
    }

    private static void requireNotAlreadySwapped(
            ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment) {
        if (SWAPPED.equals(requesterAssignment.getAssignmentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, REQUESTER_ASSIGNMENT_ALREADY_SWAPPED);
        }
        if (SWAPPED.equals(targetAssignment.getAssignmentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, TARGET_ASSIGNMENT_ALREADY_SWAPPED);
        }
    }

    private static void requireDifferentWorkShifts(
            ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment) {
        if (requesterAssignment.getWorkShift().getId().equals(targetAssignment.getWorkShift().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SAME_WORK_SHIFT);
        }
    }

    private void requireNoCrossShiftCollision(
            ShiftAssignment requesterAssignment, ShiftAssignment targetAssignment,
            Employee requester, Employee target) {
        Long requesterShiftId = requesterAssignment.getWorkShift().getId();
        Long targetShiftId = targetAssignment.getWorkShift().getId();
        if (shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(requesterShiftId, target.getId())
                || shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(
                        targetShiftId, requester.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CROSS_SHIFT_COLLISION);
        }
    }

    private static void requirePublishedPeriod(ShiftAssignment assignment) {
        if (!PUBLISHED.equals(assignment.getWorkShift().getSchedulePeriod().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, NOT_PUBLISHED_PERIOD);
        }
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
        ShiftAssignment targetAssignment = swapRequest.getTargetAssignment();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", swapRequest.getId());
        payload.put("status", swapRequest.getStatus());
        payload.put("requesterAssignmentId", swapRequest.getRequesterAssignment().getId());
        payload.put("targetAssignmentId", targetAssignment == null ? null : targetAssignment.getId());
        payload.put("targetEmployeeId", swapRequest.getTargetEmployee().getId());
        activityLogService.record(manager, action, "swap_requests", swapRequest.getId(), null, payload);
    }

    private static List<Account> swapParties(Employee requester, Employee target) {
        return Arrays.asList(requester.getAccount(), target.getAccount());
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

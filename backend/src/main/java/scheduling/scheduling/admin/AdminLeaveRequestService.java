package scheduling.scheduling.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;

@Service
public class AdminLeaveRequestService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public AdminLeaveRequestService(LeaveRequestRepository leaveRequestRepository, AccountRepository accountRepository) {
        this(leaveRequestRepository, accountRepository, Clock.systemDefaultZone());
    }

    AdminLeaveRequestService(LeaveRequestRepository leaveRequestRepository, AccountRepository accountRepository, Clock clock) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminLeaveRequestResponse> listLeaveRequests(
            CurrentAccountPrincipal principal, Long employeeId, String status, LocalDate from, LocalDate to) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return leaveRequestRepository.searchForAdmin(employeeId, normalizedStatus, from, to).stream()
                .map(AdminLeaveRequestResponse::from)
                .toList();
    }

    @Transactional
    public AdminLeaveRequestResponse approveLeaveRequest(
            CurrentAccountPrincipal principal, Long leaveRequestId, ReviewLeaveRequestRequest request) {
        Account manager = requireManager(principal);
        LeaveRequest leaveRequest = findForUpdate(leaveRequestId);
        requirePending(leaveRequest);
        Long employeeId = leaveRequest.getEmployee().getId();
        serializeEmployeeReview(leaveRequest.getEmployee());
        if (leaveRequestRepository.existsApprovedOverlapExcludingId(
                employeeId, leaveRequest.getStartDate(), leaveRequest.getEndDate(), leaveRequest.getId())) {
            throw invalidRequest();
        }
        applyReview(leaveRequest, manager, APPROVED, request.reviewNote());
        return AdminLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public AdminLeaveRequestResponse rejectLeaveRequest(
            CurrentAccountPrincipal principal, Long leaveRequestId, ReviewLeaveRequestRequest request) {
        Account manager = requireManager(principal);
        LeaveRequest leaveRequest = findForUpdate(leaveRequestId);
        requirePending(leaveRequest);
        applyReview(leaveRequest, manager, REJECTED, request.reviewNote());
        return AdminLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    private void serializeEmployeeReview(Employee employee) {
        Account employeeAccount = employee.getAccount();
        if (employeeAccount != null
                && accountRepository.findByIdForUpdate(employeeAccount.getId()).isPresent()) {
            return;
        }
        leaveRequestRepository.lockEmployeeLeaveRequestsForUpdate(employee.getId());
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private LeaveRequest findForUpdate(Long leaveRequestId) {
        return leaveRequestRepository.findByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
    }

    private static void requirePending(LeaveRequest leaveRequest) {
        if (!PENDING.equals(leaveRequest.getStatus())) {
            throw invalidRequest();
        }
    }

    private void applyReview(LeaveRequest leaveRequest, Account manager, String status, String reviewNote) {
        leaveRequest.setStatus(status);
        leaveRequest.setReviewedBy(manager);
        leaveRequest.setReviewedAt(LocalDateTime.now(clock));
        leaveRequest.setReviewNote(StringUtils.hasText(reviewNote) ? reviewNote.trim() : null);
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave review request");
    }
}

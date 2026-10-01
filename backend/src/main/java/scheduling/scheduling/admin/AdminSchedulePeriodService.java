package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.notification.NotificationService;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.OptimizationRunRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminSchedulePeriodService {

    private static final String DRAFT = "DRAFT";
    private static final String APPROVED = "APPROVED";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";
    private static final String INFEASIBLE = "INFEASIBLE";
    private static final String ACTIVE = "ACTIVE";
    private static final String INFEASIBLE_APPROVE_MESSAGE =
            "Lịch đang có ràng buộc chưa thỏa nên không thể duyệt.";
    private static final Set<String> LISTABLE_STATUSES =
            Set.of(DRAFT, APPROVED, PUBLISHED, CANCELLED);

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AccountRepository accountRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final OptimizationRunRepository optimizationRunRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final EmployeeRepository employeeRepository;

    public AdminSchedulePeriodService(
            SchedulePeriodRepository schedulePeriodRepository,
            AccountRepository accountRepository,
            WorkShiftRepository workShiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            OptimizationRunRepository optimizationRunRepository,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            EmployeeRepository employeeRepository) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.accountRepository = accountRepository;
        this.workShiftRepository = workShiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.optimizationRunRepository = optimizationRunRepository;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public AdminSchedulePeriodResponse createSchedulePeriod(CurrentAccountPrincipal principal, SchedulePeriodRequest request) {
        Account manager = requireManager(principal);
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = normalizeVersionNo(request.versionNo());
        validatePeriodRange(request.startDate(), request.endDate());
        if (schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                request.startDate(), request.endDate(), versionNo)) {
            throw invalidRequest();
        }

        SchedulePeriod period = SchedulePeriod.builder()
                .periodName(periodName)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .versionNo(versionNo)
                .status(DRAFT)
                .createdBy(manager)
                .build();
        return toResponse(savePeriod(period));
    }

    @Transactional(readOnly = true)
    public List<AdminSchedulePeriodResponse> listSchedulePeriods(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        validateFilterRange(from, to);
        return schedulePeriodRepository.searchForAdmin(normalizedStatus, from, to).stream()
                .map(period -> toResponse(period))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSchedulePeriodResponse getSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        return toResponse(findDetail(periodId));
    }

    @Transactional
    public AdminSchedulePeriodResponse updateSchedulePeriod(
            CurrentAccountPrincipal principal, Long periodId, SchedulePeriodRequest request) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = normalizeVersionNo(request.versionNo());
        validatePeriodRange(request.startDate(), request.endDate());
        if (schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                request.startDate(), request.endDate(), versionNo, periodId)) {
            throw invalidRequest();
        }

        period.setPeriodName(periodName);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setVersionNo(versionNo);
        return toResponse(savePeriod(period));
    }

    @Transactional
    public AdminSchedulePeriodResponse approveSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        Account manager = requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus()) && !APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        validateNotInfeasible(periodId);
        validateStaffing(periodId);
        if (APPROVED.equals(period.getStatus())) {
            return toResponse(period);
        }
        period.setStatus(APPROVED);
        period.setApprovedBy(manager);
        return toResponse(savePeriod(period));
    }

    @Transactional
    public AdminSchedulePeriodResponse publishSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        Account manager = requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (PUBLISHED.equals(period.getStatus())) {
            return toResponse(period);
        }
        if (!APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        period.setStatus(PUBLISHED);
        period.setPublishedAt(LocalDateTime.now());
        List<Account> recipients = shiftAssignmentRepository.listForPeriod(periodId).stream()
                .map(assignment -> assignment.getEmployee() == null ? null : assignment.getEmployee().getAccount())
                .filter(Objects::nonNull)
                .filter(account -> account.getId() != null)
                .collect(Collectors.toMap(
                        Account::getId, account -> account, (first, second) -> first, LinkedHashMap::new))
                .values().stream()
                .toList();
        notificationService.notifyAll(recipients, "SCHEDULE_PUBLISHED", "Lịch làm việc mới đã được công bố",
                "Lịch làm việc kỳ \"" + period.getPeriodName() + "\" đã được công bố. Vui lòng kiểm tra lịch cá nhân.");
        activityLogService.record(manager, "PUBLISH", "schedule_periods", period.getId(), null, Map.of(
                "id", period.getId(),
                "periodName", period.getPeriodName(),
                "status", period.getStatus()));
        return toResponse(savePeriod(period));
    }

    @Transactional
    public AdminSchedulePeriodResponse cancelSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (CANCELLED.equals(period.getStatus())) {
            return toResponse(period);
        }
        if (!DRAFT.equals(period.getStatus()) && !APPROVED.equals(period.getStatus())) {
            throw invalidRequest();
        }
        period.setStatus(CANCELLED);
        return toResponse(savePeriod(period));
    }

    @Transactional
    public AdminSchedulePeriodResponse updateEmployeePool(
            CurrentAccountPrincipal principal, Long periodId, List<Long> employeeIds) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách nhân viên không được để trống.");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(employeeIds);
        List<Employee> employees = employeeRepository.findAllById(distinctIds);
        if (employees.size() != distinctIds.size()
                || employees.stream().anyMatch(employee -> !isSchedulableForAi(employee))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách nhân viên không hợp lệ.");
        }
        Map<Long, Employee> byId = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        period.setEmployeePool(distinctIds.stream()
                .map(byId::get)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return toResponse(savePeriod(period));
    }

    private void validateNotInfeasible(Long periodId) {
        optimizationRunRepository.findTopBySchedulePeriodIdOrderByStartedAtDescIdDesc(periodId)
                .filter(run -> INFEASIBLE.equals(run.getStatus()))
                .ifPresent(run -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, INFEASIBLE_APPROVE_MESSAGE);
                });
    }

    private String resolveOptimizationStatus(Long periodId) {
        return optimizationRunRepository.findTopBySchedulePeriodIdOrderByStartedAtDescIdDesc(periodId)
                .map(OptimizationRun::getStatus)
                .orElse(null);
    }

    private AdminSchedulePeriodResponse toResponse(SchedulePeriod period) {
        return AdminSchedulePeriodResponse.from(period, resolveOptimizationStatus(period.getId()));
    }

    private void validateStaffing(Long periodId) {
        List<WorkShift> shifts = workShiftRepository.searchForPeriod(periodId, null, null);
        if (shifts.isEmpty()) {
            throw invalidRequest();
        }
        Map<Long, Long> assignmentCounts = shiftAssignmentRepository.listForPeriod(periodId).stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getWorkShift().getId(), Collectors.counting()));
        for (WorkShift shift : shifts) {
            if (assignmentCounts.getOrDefault(shift.getId(), 0L) < shift.getRequiredStaff()) {
                throw invalidRequest();
            }
        }
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private SchedulePeriod findDetail(Long periodId) {
        return schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
    }

    private SchedulePeriod savePeriod(SchedulePeriod period) {
        try {
            return schedulePeriodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException ex) {
            throw invalidRequest();
        }
    }

    private static String normalizePeriodName(String periodName) {
        if (!StringUtils.hasText(periodName)) {
            throw invalidRequest();
        }
        String normalized = periodName.trim();
        if (normalized.length() > 100) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static int normalizeVersionNo(Integer versionNo) {
        int normalized = versionNo == null ? 1 : versionNo;
        if (normalized < 1) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return DRAFT;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!LISTABLE_STATUSES.contains(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static void validatePeriodRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw invalidRequest();
        }
        if (startDate.isAfter(endDate)) {
            throw invalidRequest();
        }
    }

    private static void validateFilterRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày bắt đầu lọc phải trước hoặc bằng ngày kết thúc.");
        }
    }

    private static boolean isSchedulableForAi(Employee employee) {
        Account account = employee.getAccount();
        return ACTIVE.equals(employee.getStatus())
                && account != null
                && AccountRole.EMPLOYEE.equals(account.getRole())
                && AccountStatus.ACTIVE.equals(account.getStatus())
                && Boolean.TRUE.equals(account.getIsActive());
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule period request");
    }
}

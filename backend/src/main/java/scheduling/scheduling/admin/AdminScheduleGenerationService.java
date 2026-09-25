package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.GenerateScheduleRequest;
import scheduling.scheduling.admin.dto.GenerateScheduleResponse;
import scheduling.scheduling.admin.dto.OptimizeScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.OptimizationRunRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminScheduleGenerationService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String CANCELLED = "CANCELLED";

    private final AccountRepository accountRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final WorkShiftRepository workShiftRepository;
    private final OptimizationRunRepository optimizationRunRepository;
    private final DayClassificationService dayClassificationService;
    private final AdminOptimizationService optimizationService;
    private final TransactionTemplate transactionTemplate;

    public AdminScheduleGenerationService(
            AccountRepository accountRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            ShiftTypeRepository shiftTypeRepository,
            WorkShiftRepository workShiftRepository,
            OptimizationRunRepository optimizationRunRepository,
            DayClassificationService dayClassificationService,
            AdminOptimizationService optimizationService,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this.accountRepository = accountRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.workShiftRepository = workShiftRepository;
        this.optimizationRunRepository = optimizationRunRepository;
        this.dayClassificationService = dayClassificationService;
        this.optimizationService = optimizationService;
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public GenerateScheduleResponse generate(CurrentAccountPrincipal principal, GenerateScheduleRequest request) {
        Account manager = requireManager(principal);
        validatePeriodRange(request.startDate(), request.endDate());
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = request.versionNo() == null ? 1 : request.versionNo();
        if (versionNo < 1) {
            throw invalidRequest();
        }
        List<Employee> employees = employeeRepository.findAllById(request.employeeIds());
        if (employees.size() != new HashSet<>(request.employeeIds()).size() || employees.stream().anyMatch(employee -> !ACTIVE.equals(employee.getStatus()))) {
            throw invalidRequest();
        }
        List<ShiftType> shiftTypes = shiftTypeRepository.findAllById(request.shiftTypeIds());
        if (shiftTypes.size() != new HashSet<>(request.shiftTypeIds()).size() || shiftTypes.stream().anyMatch(shiftType -> !ACTIVE.equals(shiftType.getStatus()))) {
            throw invalidRequest();
        }

        PreparedPeriod saved = preparePeriod(request, manager, periodName, versionNo, employees, shiftTypes);
        OptimizeScheduleResponse optimized = optimizationService.optimize(principal, saved.id(), new OptimizeScheduleRequest(null, request.employeeIds()));
        return new GenerateScheduleResponse(
                saved.id(),
                saved.periodName(),
                saved.status(),
                optimized.aiStatus(),
                optimized.solverStatus(),
                optimized.assignmentCount(),
                optimized.warnings(),
                optimized.errorMessage());
    }

    private PreparedPeriod preparePeriod(
            GenerateScheduleRequest request,
            Account manager,
            String periodName,
            int versionNo,
            List<Employee> employees,
            List<ShiftType> shiftTypes) {
        if (transactionTemplate == null) {
            return preparePeriodInTransaction(request, manager, periodName, versionNo, employees, shiftTypes);
        }
        return transactionTemplate.execute(status -> preparePeriodInTransaction(
                request, manager, periodName, versionNo, employees, shiftTypes));
    }

    private PreparedPeriod preparePeriodInTransaction(
            GenerateScheduleRequest request,
            Account manager,
            String periodName,
            int versionNo,
            List<Employee> employees,
            List<ShiftType> shiftTypes) {
        List<SchedulePeriod> matchingPeriods = schedulePeriodRepository.findAllByStartDateAndEndDateAndVersionNoForUpdate(
                request.startDate(), request.endDate(), versionNo);
        if (matchingPeriods.stream().anyMatch(period -> !CANCELLED.equals(period.getStatus()))) {
            throw duplicatePeriodRequest();
        }

        SchedulePeriod saved = savePeriod(preparePeriodEntity(
                matchingPeriods, request, manager, periodName, versionNo, employees));
        createWorkShifts(saved, shiftTypes);
        return new PreparedPeriod(saved.getId(), saved.getPeriodName(), saved.getStatus());
    }

    private SchedulePeriod preparePeriodEntity(
            List<SchedulePeriod> matchingPeriods,
            GenerateScheduleRequest request,
            Account manager,
            String periodName,
            int versionNo,
            List<Employee> employees) {
        SchedulePeriod period = matchingPeriods.stream().findFirst().orElseGet(SchedulePeriod::new);
        if (period.getId() != null) {
            resetGeneratedState(period);
        }
        period.setPeriodName(periodName);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setVersionNo(versionNo);
        period.setStatus(DRAFT);
        period.setCreatedBy(manager);
        period.setApprovedBy(null);
        period.setPublishedAt(null);
        period.setTotalPenalty(null);
        period.setEmployeePool(new LinkedHashSet<>(employees));
        return period;
    }

    private record PreparedPeriod(Long id, String periodName, String status) {
    }

    private void resetGeneratedState(SchedulePeriod period) {
        workShiftRepository.deleteAll(workShiftRepository.searchForPeriod(period.getId(), null, null));
        optimizationRunRepository.deleteAll(optimizationRunRepository.findAllBySchedulePeriodId(period.getId()));
    }

    private void createWorkShifts(SchedulePeriod period, List<ShiftType> shiftTypes) {
        for (LocalDate date = period.getStartDate(); !date.isAfter(period.getEndDate()); date = date.plusDays(1)) {
            for (ShiftType shiftType : shiftTypes) {
                if (!isApplicable(date, shiftType)) {
                    continue;
                }
                WorkShiftWindowService.Window window = new WorkShiftWindowService().derive(date, shiftType);
                workShiftRepository.save(WorkShift.builder()
                        .schedulePeriod(period)
                        .shiftType(shiftType)
                        .workDate(date)
                        .requiredStaff(shiftType.getMinStaff())
                        .note("Generated from schedule generation")
                        .startsAt(window.startsAt())
                        .endsAt(window.endsAt())
                        .workMinutes(window.workMinutes())
                        .transitionShift(window.transitionShift())
                        .build());
            }
        }
    }

    private boolean isApplicable(LocalDate date, ShiftType shiftType) {
        DayClassificationService.DayType dayType = dayClassificationService.classify(date);
        if (dayType == DayClassificationService.DayType.HOLIDAY) {
            return Boolean.TRUE.equals(shiftType.getAppliesOnHolidays());
        }
        return ShiftTypeRules.includesWeekday(shiftType.getApplicableWeekdays(), date.getDayOfWeek());
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

    private SchedulePeriod savePeriod(SchedulePeriod period) {
        try {
            return schedulePeriodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException ex) {
            throw duplicatePeriodRequest();
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

    private static void validatePeriodRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw invalidRequest();
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule generation request");
    }

    private static ResponseStatusException duplicatePeriodRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đã tồn tại lịch trong khoảng ngày này với cùng phiên bản");
    }
}

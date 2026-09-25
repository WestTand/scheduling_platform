package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminScheduleGenerationService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";

    private final AccountRepository accountRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final WorkShiftRepository workShiftRepository;
    private final DayClassificationService dayClassificationService;
    private final AdminOptimizationService optimizationService;

    public AdminScheduleGenerationService(
            AccountRepository accountRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            ShiftTypeRepository shiftTypeRepository,
            WorkShiftRepository workShiftRepository,
            DayClassificationService dayClassificationService,
            AdminOptimizationService optimizationService) {
        this.accountRepository = accountRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.workShiftRepository = workShiftRepository;
        this.dayClassificationService = dayClassificationService;
        this.optimizationService = optimizationService;
    }

    public GenerateScheduleResponse generate(CurrentAccountPrincipal principal, GenerateScheduleRequest request) {
        Account manager = requireManager(principal);
        validatePeriodRange(request.startDate(), request.endDate());
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = request.versionNo() == null ? 1 : request.versionNo();
        if (versionNo < 1 || schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(request.startDate(), request.endDate(), versionNo)) {
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

        SchedulePeriod period = SchedulePeriod.builder()
                .periodName(periodName)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .versionNo(versionNo)
                .status(DRAFT)
                .createdBy(manager)
                .employeePool(new LinkedHashSet<>(employees))
                .build();
        SchedulePeriod saved = savePeriod(period);
        createWorkShifts(saved, shiftTypes);

        OptimizeScheduleResponse optimized = optimizationService.optimize(principal, saved.getId(), new OptimizeScheduleRequest(null, request.employeeIds()));
        return new GenerateScheduleResponse(
                saved.getId(),
                saved.getPeriodName(),
                saved.getStatus(),
                optimized.aiStatus(),
                optimized.solverStatus(),
                optimized.assignmentCount(),
                optimized.warnings(),
                optimized.errorMessage());
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

    private static void validatePeriodRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw invalidRequest();
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule generation request");
    }
}

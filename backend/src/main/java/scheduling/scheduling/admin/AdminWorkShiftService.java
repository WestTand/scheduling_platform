package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminWorkShiftService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_NOTE_LENGTH = 1000;

    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final AccountRepository accountRepository;
    private final DayClassificationService dayClassificationService;

    public AdminWorkShiftService(
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            ShiftTypeRepository shiftTypeRepository,
            AccountRepository accountRepository,
            DayClassificationService dayClassificationService) {
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.accountRepository = accountRepository;
        this.dayClassificationService = dayClassificationService;
    }

    @Transactional
    public AdminWorkShiftResponse createWorkShift(
            CurrentAccountPrincipal principal, Long periodId, CreateWorkShiftRequest request) {
        requireManager(principal);
        SchedulePeriod period = findPeriod(periodId);
        requireDraft(period);
        ShiftType shiftType = findShiftType(request.shiftTypeId());
        requireActiveShiftType(shiftType);
        validateDateInPeriod(request.workDate(), period);
        validateShiftApplicability(request.workDate(), shiftType);
        int requiredStaff = normalizeRequiredStaff(request.requiredStaff(), shiftType);
        String note = normalizeNote(request.note());
        WorkShiftWindowService.Window window = new WorkShiftWindowService().derive(request.workDate(), shiftType);

        WorkShift workShift = WorkShift.builder()
                .schedulePeriod(period)
                .shiftType(shiftType)
                .workDate(request.workDate())
                .requiredStaff(requiredStaff)
                .note(note)
                .startsAt(window.startsAt())
                .endsAt(window.endsAt())
                .workMinutes(window.workMinutes())
                .transitionShift(window.transitionShift())
                .build();
        return AdminWorkShiftResponse.from(workShiftRepository.save(workShift));
    }

    @Transactional(readOnly = true)
    public List<AdminWorkShiftResponse> listWorkShifts(
            CurrentAccountPrincipal principal, Long periodId, LocalDate date, Long shiftTypeId) {
        requireManager(principal);
        SchedulePeriod period = findPeriod(periodId);
        if (date != null) {
            validateDateInPeriod(date, period);
        }
        return workShiftRepository.searchForPeriod(periodId, date, shiftTypeId).stream()
                .map(AdminWorkShiftResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminWorkShiftResponse getWorkShift(CurrentAccountPrincipal principal, Long workShiftId) {
        requireManager(principal);
        WorkShift workShift = workShiftRepository.findDetailById(workShiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work shift not found"));
        return AdminWorkShiftResponse.from(workShift);
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

    private SchedulePeriod findPeriod(Long periodId) {
        return schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
    }

    private ShiftType findShiftType(Long shiftTypeId) {
        return shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift type not found"));
    }

    private static void requireDraft(SchedulePeriod period) {
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
    }

    private static void requireActiveShiftType(ShiftType shiftType) {
        if (!ACTIVE.equals(shiftType.getStatus())) {
            throw invalidRequest();
        }
    }

    private void validateShiftApplicability(LocalDate workDate, ShiftType shiftType) {
        DayClassificationService.DayType dayType = dayClassificationService.classify(workDate);
        if (dayType == DayClassificationService.DayType.HOLIDAY) {
            if (!Boolean.TRUE.equals(shiftType.getAppliesOnHolidays())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Shift type is not applicable on this date");
            }
            return;
        }
        if (!ShiftTypeRules.includesWeekday(shiftType.getApplicableWeekdays(), workDate.getDayOfWeek())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Shift type is not applicable on this date");
        }
    }

    private static void validateDateInPeriod(LocalDate workDate, SchedulePeriod period) {
        if (workDate == null || workDate.isBefore(period.getStartDate()) || workDate.isAfter(period.getEndDate())) {
            throw invalidRequest();
        }
    }

    private static int normalizeRequiredStaff(Integer requiredStaff, ShiftType shiftType) {
        int normalized = requiredStaff == null ? shiftType.getMinStaff() : requiredStaff;
        if (normalized < shiftType.getMinStaff() || normalized > shiftType.getMaxStaff()) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid work shift request");
    }
}

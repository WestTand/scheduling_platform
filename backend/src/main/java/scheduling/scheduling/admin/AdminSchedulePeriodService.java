package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@Service
public class AdminSchedulePeriodService {

    private static final String DRAFT = "DRAFT";

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AccountRepository accountRepository;

    public AdminSchedulePeriodService(SchedulePeriodRepository schedulePeriodRepository, AccountRepository accountRepository) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AdminSchedulePeriodResponse createSchedulePeriod(CurrentAccountPrincipal principal, SchedulePeriodRequest request) {
        Account manager = requireManager(principal);
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = normalizeVersionNo(request.versionNo());
        validateRange(request.startDate(), request.endDate());
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
        return AdminSchedulePeriodResponse.from(schedulePeriodRepository.save(period));
    }

    @Transactional(readOnly = true)
    public List<AdminSchedulePeriodResponse> listSchedulePeriods(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        validateRange(from, to);
        return schedulePeriodRepository.searchForAdmin(normalizedStatus, from, to).stream()
                .map(AdminSchedulePeriodResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSchedulePeriodResponse getSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        return AdminSchedulePeriodResponse.from(findDetail(periodId));
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
        validateRange(request.startDate(), request.endDate());
        if (schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                request.startDate(), request.endDate(), versionNo, periodId)) {
            throw invalidRequest();
        }

        period.setPeriodName(periodName);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setVersionNo(versionNo);
        return AdminSchedulePeriodResponse.from(schedulePeriodRepository.save(period));
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
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!DRAFT.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule period request");
    }
}

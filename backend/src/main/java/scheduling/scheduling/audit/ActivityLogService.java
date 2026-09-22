package scheduling.scheduling.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.ActivityLog;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ActivityLogRepository;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public ActivityLogService(ActivityLogRepository activityLogRepository, AccountRepository accountRepository, ObjectMapper objectMapper) {
        this.activityLogRepository = activityLogRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional
    public void record(Account actor, String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        if (actor == null || actor.getId() == null) {
            return;
        }
        activityLogRepository.save(ActivityLog.builder()
                .account(actor)
                .action(requireText(action, "Action is required"))
                .entityType(requireText(entityType, "Entity type is required"))
                .entityId(entityId)
                .oldValueJson(writeNullable(oldValue))
                .newValueJson(writeNullable(newValue))
                .build());
    }

    @Transactional(readOnly = true)
    public List<ActivityLogResponse> list(
            CurrentAccountPrincipal principal,
            String action,
            String entityType,
            Long accountId,
            LocalDate from,
            LocalDate to) {
        requireManager(principal);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid activity log filter");
        }
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        String normalizedAction = StringUtils.hasText(action) ? action.trim().toUpperCase(java.util.Locale.ROOT) : null;
        String normalizedEntityType = StringUtils.hasText(entityType) ? entityType.trim() : null;
        return activityLogRepository.search(normalizedAction, normalizedEntityType, accountId, fromDateTime, toExclusive).stream()
                .map(ActivityLogResponse::from)
                .toList();
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER && account.getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private String writeNullable(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize activity log snapshot", ex);
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

package scheduling.scheduling.audit;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.ActivityLog;

public record ActivityLogResponse(
        Long id,
        Long accountId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String oldValueJson,
        String newValueJson,
        LocalDateTime createdAt,
        String ipAddress) {

    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getAccount().getId(),
                log.getAccount().getUsername(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValueJson(),
                log.getNewValueJson(),
                log.getCreatedAt(),
                log.getIpAddress());
    }
}

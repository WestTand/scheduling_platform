package scheduling.scheduling.notification;

import java.time.LocalDateTime;
import scheduling.scheduling.entity.Notification;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        String notificationType,
        Boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getNotificationType(),
                notification.getRead(),
                notification.getCreatedAt());
    }
}

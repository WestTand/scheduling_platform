package scheduling.scheduling.notification;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Notification;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;

    public NotificationService(NotificationRepository notificationRepository, AccountRepository accountRepository) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void notify(Account recipient, String type, String title, String content) {
        if (recipient == null || recipient.getId() == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .notificationType(requireText(type, "Notification type is required"))
                .title(requireText(title, "Notification title is required"))
                .content(StringUtils.hasText(content) ? content.trim() : null)
                .read(false)
                .build());
    }

    @Transactional
    public void notifyAll(Collection<Account> recipients, String type, String title, String content) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        Map<Long, Account> uniqueRecipients = new LinkedHashMap<>();
        for (Account recipient : recipients) {
            if (recipient != null && recipient.getId() != null) {
                uniqueRecipients.putIfAbsent(recipient.getId(), recipient);
            }
        }
        uniqueRecipients.values().forEach(recipient -> notify(recipient, type, title, content));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(CurrentAccountPrincipal principal, Boolean read) {
        Account account = requireAccount(principal);
        return notificationRepository.findForRecipient(account.getId(), read).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(CurrentAccountPrincipal principal, Long notificationId) {
        Account account = requireAccount(principal);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(CurrentAccountPrincipal principal) {
        Account account = requireAccount(principal);
        notificationRepository.markAllRead(account.getId());
    }

    private Account requireAccount(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        return accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required"));
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

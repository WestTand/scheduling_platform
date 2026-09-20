package scheduling.scheduling.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Notification;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.NotificationRepository;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final NotificationService service = new NotificationService(notificationRepository, accountRepository);

    @Test
    void listsOnlyCurrentAccountNotificationsWithReadFilter() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        Notification notification = notification(100L, account, "LEAVE_APPROVED", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findForRecipient(10L, false)).thenReturn(List.of(notification));

        List<NotificationResponse> result = service.list(principal(10L, AccountRole.EMPLOYEE), false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).notificationType()).isEqualTo("LEAVE_APPROVED");
        assertThat(result.get(0).read()).isFalse();
    }

    @Test
    void marksOwnNotificationRead() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        Notification notification = notification(100L, account, "SWAP_REQUESTED", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findByIdAndRecipientId(100L, 10L)).thenReturn(Optional.of(notification));

        NotificationResponse result = service.markRead(principal(10L, AccountRole.EMPLOYEE), 100L);

        assertThat(notification.getRead()).isTrue();
        assertThat(result.read()).isTrue();
    }

    @Test
    void rejectsMarkingAnotherUsersNotification() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(notificationRepository.findByIdAndRecipientId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(principal(10L, AccountRole.EMPLOYEE), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void marksAllOwnNotificationsRead() {
        Account account = account(10L, AccountRole.EMPLOYEE);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        service.markAllRead(principal(10L, AccountRole.EMPLOYEE));

        verify(notificationRepository).markAllRead(10L);
    }

    private static CurrentAccountPrincipal principal(Long accountId, AccountRole role) {
        return new CurrentAccountPrincipal(accountId, "user" + accountId, role);
    }

    private static Account account(Long id, AccountRole role) {
        Account account = Account.builder().username("user" + id).passwordHash("hash").role(role).build();
        account.setId(id);
        return account;
    }

    private static Notification notification(Long id, Account recipient, String type, boolean read) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .title("Title")
                .content("Content")
                .notificationType(type)
                .read(read)
                .build();
        notification.setId(id);
        notification.setCreatedAt(LocalDateTime.of(2026, 9, 20, 9, 0));
        return notification;
    }
}

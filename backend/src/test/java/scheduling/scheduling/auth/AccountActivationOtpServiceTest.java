package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountActivationOtp;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountActivationOtpRepository;

class AccountActivationOtpServiceTest {

    private final AccountActivationOtpRepository otpRepository = mock(AccountActivationOtpRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountMailService accountMailService = mock(AccountMailService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneId.of("UTC"));
    private final AccountActivationOtpService service = new AccountActivationOtpService(
            otpRepository,
            passwordEncoder,
            accountMailService,
            clock);

    @Test
    void createActivationOtpStoresHashedSixDigitOtpAndEmailsRawOtp() {
        Account account = account();
        when(passwordEncoder.encode(any(String.class))).thenReturn("otp-hash");

        service.createActivationOtp(account);

        ArgumentCaptor<String> rawOtpCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(rawOtpCaptor.capture());
        assertThat(rawOtpCaptor.getValue()).matches("\\d{6}");

        ArgumentCaptor<AccountActivationOtp> otpCaptor = ArgumentCaptor.forClass(AccountActivationOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getAccount()).isSameAs(account);
        assertThat(otpCaptor.getValue().getOtpHash()).isEqualTo("otp-hash");
        assertThat(otpCaptor.getValue().getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 9, 0, 10));
        assertThat(otpCaptor.getValue().getUsedAt()).isNull();

        verify(accountMailService).sendActivationOtp(account, rawOtpCaptor.getValue());
    }

    @Test
    void verifyActivationOtpMarksOtpUsedAndMovesAccountToForcePasswordChange() {
        Account account = account();
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        AccountActivationOtp otp = AccountActivationOtp.builder()
                .account(account)
                .otpHash("otp-hash")
                .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
                .build();
        when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);

        Account result = service.verifyActivationOtp(20L, "123456");

        assertThat(result).isSameAs(account);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FORCE_PASSWORD_CHANGE);
        assertThat(otp.getUsedAt()).isEqualTo(LocalDateTime.of(2026, 9, 9, 0, 0));
        verify(otpRepository).save(otp);
    }

    @Test
    void verifyActivationOtpRejectsWrongOtp() {
        Account account = account();
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        AccountActivationOtp otp = AccountActivationOtp.builder()
                .account(account)
                .otpHash("otp-hash")
                .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
                .build();
        when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyActivationOtp(20L, "000000"))
                .isInstanceOf(InvalidActivationOtpException.class);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(otp.getUsedAt()).isNull();
    }

    @Test
    void verifyActivationOtpRejectsExpiredOtp() {
        Account account = account();
        account.setStatus(AccountStatus.PENDING_ACTIVATION);
        AccountActivationOtp otp = AccountActivationOtp.builder()
                .account(account)
                .otpHash("otp-hash")
                .expiresAt(LocalDateTime.of(2026, 9, 8, 23, 59))
                .build();
        when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyActivationOtp(20L, "123456"))
                .isInstanceOf(InvalidActivationOtpException.class);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(otp.getUsedAt()).isNull();
    }

    @Test
    void verifyActivationOtpRejectsNonPendingAccount() {
        Account account = account();
        account.setStatus(AccountStatus.ACTIVE);
        AccountActivationOtp otp = AccountActivationOtp.builder()
                .account(account)
                .otpHash("otp-hash")
                .expiresAt(LocalDateTime.of(2026, 9, 9, 0, 10))
                .build();
        when(otpRepository.findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(20L))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyActivationOtp(20L, "123456"))
                .isInstanceOf(InvalidActivationOtpException.class);
    }

    private static Account account() {
        Employee employee = Employee.builder()
                .email("employee01@gmail.com")
                .fullName("Nguyen Van A")
                .build();
        Account account = Account.builder()
                .username("employee01")
                .role(AccountRole.EMPLOYEE)
                .employee(employee)
                .build();
        account.setId(20L);
        return account;
    }
}

package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountActivationOtp;
import scheduling.scheduling.entity.AccountRole;
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

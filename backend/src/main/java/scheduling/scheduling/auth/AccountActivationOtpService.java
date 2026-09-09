package scheduling.scheduling.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountActivationOtp;
import scheduling.scheduling.repository.AccountActivationOtpRepository;

@Service
public class AccountActivationOtpService {

    private static final int OTP_BOUND = 1_000_000;
    private static final int OTP_TTL_MINUTES = 10;

    private final AccountActivationOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailService accountMailService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountActivationOtpService(
            AccountActivationOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            AccountMailService accountMailService,
            Clock clock) {
        this.otpRepository = Objects.requireNonNull(otpRepository, "otpRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.accountMailService = Objects.requireNonNull(accountMailService, "accountMailService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void createActivationOtp(Account account) {
        String rawOtp = String.format("%06d", secureRandom.nextInt(OTP_BOUND));
        AccountActivationOtp otp = AccountActivationOtp.builder()
                .account(account)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(LocalDateTime.now(clock).plusMinutes(OTP_TTL_MINUTES))
                .build();
        otpRepository.save(otp);
        accountMailService.sendActivationOtp(account, rawOtp);
    }
}

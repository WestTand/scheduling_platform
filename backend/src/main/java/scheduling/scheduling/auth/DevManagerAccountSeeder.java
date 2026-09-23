package scheduling.scheduling.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@Component
@Profile("dev")
public class DevManagerAccountSeeder implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public DevManagerAccountSeeder(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_MANAGER_USERNAME:}") String username,
            @Value("${SEED_MANAGER_PASSWORD:}") String password) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }

        Account account = accountRepository.findByUsername(username.trim())
                .orElseGet(() -> Account.builder().username(username.trim()).build());
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(AccountRole.MANAGER);
        account.setIsActive(true);
        account.setStatus(AccountStatus.ACTIVE);

        accountRepository.save(account);
    }
}

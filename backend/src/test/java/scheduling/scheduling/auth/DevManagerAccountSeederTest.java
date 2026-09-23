package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

class DevManagerAccountSeederTest {

    private final AccountRepository accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);

    @Test
    void createsManagerWhenConfiguredAccountDoesNotExist() throws Exception {
        when(accountRepository.findByUsername("manager@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Manager@123")).thenReturn("encoded-password");

        new DevManagerAccountSeeder(
                accountRepository,
                passwordEncoder,
                "manager@example.com",
                "Manager@123")
                .run(null);

        org.mockito.ArgumentCaptor<Account> accountCaptor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertThat(account.getUsername()).isEqualTo("manager@example.com");
        assertThat(account.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(account.getRole()).isEqualTo(AccountRole.MANAGER);
        assertThat(account.getIsActive()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getEmployee()).isNull();
    }

    @Test
    void updatesExistingAccountToConfiguredActiveManager() throws Exception {
        Account existing = Account.builder()
                .username("manager@example.com")
                .passwordHash("old-password")
                .role(AccountRole.EMPLOYEE)
                .isActive(false)
                .status(AccountStatus.INACTIVE)
                .build();
        when(accountRepository.findByUsername("manager@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("Manager@123")).thenReturn("new-password");

        new DevManagerAccountSeeder(
                accountRepository,
                passwordEncoder,
                "manager@example.com",
                "Manager@123")
                .run(null);

        verify(accountRepository).save(existing);
        assertThat(existing.getPasswordHash()).isEqualTo("new-password");
        assertThat(existing.getRole()).isEqualTo(AccountRole.MANAGER);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void skipsWhenCredentialsAreBlank() throws Exception {
        new DevManagerAccountSeeder(accountRepository, passwordEncoder, " ", "Manager@123").run(null);
        new DevManagerAccountSeeder(accountRepository, passwordEncoder, "manager@example.com", " ").run(null);

        verify(accountRepository, never()).findByUsername(org.mockito.ArgumentMatchers.anyString());
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any(Account.class));
    }
}

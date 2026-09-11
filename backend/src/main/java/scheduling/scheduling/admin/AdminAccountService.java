package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminAccountResponse;
import scheduling.scheduling.auth.AccountNotFoundException;
import scheduling.scheduling.auth.InvalidAccountStateException;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@Service
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminAccountService(AccountRepository accountRepository, RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAccounts(String query, String role, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        AccountRole parsedRole = parseRole(role);
        AccountStatus parsedStatus = parseStatus(status);
        return accountRepository.search(normalizedQuery, parsedRole, parsedStatus).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public void lockAccount(Long accountId) {
        Account account = findAccount(accountId);
        requireEmployeeAccount(account);
        account.setIsActive(false);
        if (account.getStatus() == AccountStatus.ACTIVE) {
            account.setStatus(AccountStatus.INACTIVE);
        }
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(accountId);
    }

    @Transactional
    public void unlockAccount(Long accountId) {
        Account account = findAccount(accountId);
        requireEmployeeAccount(account);
        account.setIsActive(true);
        if (account.getStatus() == AccountStatus.INACTIVE) {
            account.setStatus(AccountStatus.ACTIVE);
        }
        accountRepository.save(account);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
    }

    private void requireEmployeeAccount(Account account) {
        if (account.getRole() != AccountRole.EMPLOYEE) {
            throw new InvalidAccountStateException();
        }
    }

    private AccountRole parseRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        try {
            return AccountRole.valueOf(role.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account filter", exception);
        }
    }

    private AccountStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return AccountStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account filter", exception);
        }
    }
}

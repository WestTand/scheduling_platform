package scheduling.scheduling.auth.dto;

import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;

public record AccountResponse(Long id, String username, AccountRole role) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getRole());
    }
}

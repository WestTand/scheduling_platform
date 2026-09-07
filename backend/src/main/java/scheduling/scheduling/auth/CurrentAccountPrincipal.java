package scheduling.scheduling.auth;

import scheduling.scheduling.entity.AccountRole;

public record CurrentAccountPrincipal(Long accountId, String username, AccountRole role) {
}

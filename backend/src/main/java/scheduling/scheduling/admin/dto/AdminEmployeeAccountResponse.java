package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;

public record AdminEmployeeAccountResponse(
        Long id,
        String username,
        AccountRole role,
        AccountStatus status,
        Boolean active) {

    public static AdminEmployeeAccountResponse from(Account account) {
        if (account == null) {
            return null;
        }
        return new AdminEmployeeAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getStatus(),
                account.getIsActive());
    }
}

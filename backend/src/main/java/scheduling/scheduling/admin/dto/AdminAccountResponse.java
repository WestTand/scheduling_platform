package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;

public record AdminAccountResponse(
        Long id,
        String username,
        AccountRole role,
        AccountStatus status,
        Boolean active,
        AdminAccountEmployeeResponse employee) {

    public static AdminAccountResponse from(Account account) {
        Employee employee = account.getEmployee();
        return new AdminAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getStatus(),
                account.getIsActive(),
                employee == null ? null : AdminAccountEmployeeResponse.from(employee));
    }
}

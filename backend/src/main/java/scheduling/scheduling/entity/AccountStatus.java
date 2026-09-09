package scheduling.scheduling.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing account lifecycle states.
 */
@Getter
@AllArgsConstructor
public enum AccountStatus {
    ACTIVE("ACTIVE"),
    PENDING_ACTIVATION("PENDING_ACTIVATION"),
    FORCE_PASSWORD_CHANGE("FORCE_PASSWORD_CHANGE"),
    INACTIVE("INACTIVE");

    private final String value;
}
